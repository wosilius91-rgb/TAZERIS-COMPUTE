import os, json, time, base64, urllib.request, urllib.error, re, socket
from pathlib import Path
from http.client import RemoteDisconnected

OWNER="wosilius91-rgb"
REPO="AI-Document-Assistant"
MAIN="main"
BRANCH="tazeris-v5"
LOCAL="http://127.0.0.1:8080/v1/chat/completions"

TOKEN_FILE=Path.home()/".tazeris/gh_token"
GH=os.environ.get("GH_TOKEN","").strip()
if not GH and TOKEN_FILE.exists():
    GH=TOKEN_FILE.read_text().strip()

STATE=Path.home()/".tazeris/v5_state.json"

SLEEP=8
MAX_REPAIR=2

ROADMAP=[
    {
        "id":"ui_core",
        "files":[
            "app/src/main/res/layout/activity_main.xml",
            "app/src/main/res/values/strings.xml"
        ],
        "goal":
            "Create a clean commercial MVP screen with document/text input, result area "
            "and Summarize, Translate, Explain and Draft Reply controls. "
            "Every referenced id and string must exist."
    },
    {
        "id":"document_picker",
        "files":[
            "app/src/main/java/com/tazeris/aidocumentassistant/MainActivity.kt",
            "app/src/main/res/layout/activity_main.xml"
        ],
        "goal":
            "Implement ACTION_OPEN_DOCUMENT for text, PDF and image files and show the "
            "selected file state. Use Android APIs already available."
    },
    {
        "id":"text_input",
        "files":[
            "app/src/main/java/com/tazeris/aidocumentassistant/MainActivity.kt",
            "app/src/main/res/layout/activity_main.xml"
        ],
        "goal":
            "Implement pasted text plus safe reading of plain text documents from Uri "
            "and display extracted text without blocking the UI."
    },
    {
        "id":"ocr_dependency",
        "files":[
            "app/build.gradle.kts",
            "app/src/main/java/com/tazeris/aidocumentassistant/MainActivity.kt"
        ],
        "goal":
            "Add ML Kit text recognition dependency and compile-safe OCR plumbing. "
            "Do not reference layout ids that do not already exist."
    },
    {
        "id":"ocr_image",
        "files":[
            "app/src/main/java/com/tazeris/aidocumentassistant/MainActivity.kt",
            "app/src/main/res/layout/activity_main.xml"
        ],
        "goal":
            "Implement OCR for selected image Uri using the already-present OCR dependency. "
            "Handle success and failure safely."
    },
    {
        "id":"actions",
        "files":[
            "app/src/main/java/com/tazeris/aidocumentassistant/MainActivity.kt",
            "app/src/main/res/layout/activity_main.xml"
        ],
        "goal":
            "Wire Summarize, Translate, Explain and Draft Reply buttons into a clear "
            "processing pipeline. Do not fake AI results."
    },
    {
        "id":"stability",
        "files":[
            "app/src/main/java/com/tazeris/aidocumentassistant/MainActivity.kt",
            "app/src/main/AndroidManifest.xml"
        ],
        "goal":
            "Harden lifecycle, Uri handling, errors and release-safe settings."
    }
]

def state_load():
    try:
        return json.loads(STATE.read_text())
    except Exception:
        return {"done":[],"failures":{},"cycles":0,"last":""}

def state_save(s):
    STATE.parent.mkdir(parents=True,exist_ok=True)
    tmp=STATE.with_suffix(".tmp")
    tmp.write_text(json.dumps(s,ensure_ascii=False,indent=2))
    tmp.replace(STATE)

def http(url,method="GET",data=None,headers=None,timeout=180,raw=False,retries=4):
    h=dict(headers or {})
    body=None

    if data is not None:
        body=json.dumps(data).encode("utf-8")
        h["Content-Type"]="application/json"

    last=None

    for i in range(retries):
        try:
            q=urllib.request.Request(
                url,
                data=body,
                headers=h,
                method=method
            )

            with urllib.request.urlopen(q,timeout=timeout) as r:
                b=r.read()

                if raw:
                    return b.decode("utf-8","ignore")

                return json.loads(b.decode("utf-8")) if b else {}

        except urllib.error.HTTPError as e:
            last=e
            if e.code not in (408,409,429,500,502,503,504):
                raise

        except (
            urllib.error.URLError,
            RemoteDisconnected,
            ConnectionResetError,
            TimeoutError,
            socket.timeout
        ) as e:
            last=e

        time.sleep(min(20,2 ** i))

    raise last

def gh(path,method="GET",data=None,raw=False):
    return http(
        "https://api.github.com"+path,
        method,
        data,
        {
            "Authorization":f"Bearer {GH}",
            "Accept":"application/vnd.github+json",
            "X-GitHub-Api-Version":"2022-11-28"
        },
        raw=raw
    )

def ai(prompt,max_tokens=900):
    import io, zipfile

    workflow="tazeris-compute.yml"
    repo="TAZERIS-COMPUTE"

    # Paleidžiame mūsų remote compute.
    gh_compute=lambda path,method="GET",data=None: http(
        "https://api.github.com"+path,
        method,
        data,
        {
            "Authorization":f"Bearer {GH}",
            "Accept":"application/vnd.github+json",
            "X-GitHub-Api-Version":"2022-11-28"
        },
        timeout=120,
        retries=4
    )

    # Užfiksuojame laiką prieš dispatch, kad nepaimtume seno run.
    before=time.time()

    gh_compute(
        f"/repos/{OWNER}/{repo}/actions/workflows/{workflow}/dispatches",
        "POST",
        {
            "ref":"main",
            "inputs":{"prompt":prompt}
        }
    )

    run=None

    # Randame būtent naują run.
    for _ in range(60):
        d=gh_compute(
            f"/repos/{OWNER}/{repo}/actions/workflows/{workflow}/runs?event=workflow_dispatch&per_page=10"
        )

        for r in d.get("workflow_runs",[]):
            created=r.get("created_at","")
            try:
                ts=__import__("datetime").datetime.fromisoformat(
                    created.replace("Z","+00:00")
                ).timestamp()
            except Exception:
                continue

            if ts >= before-5:
                run=r
                break

        if run:
            break

        time.sleep(2)

    if not run:
        raise RuntimeError("TAZERIS COMPUTE run nerastas")

    run_id=run["id"]

    # Laukiame realaus remote rezultato.
    for _ in range(120):
        r=gh_compute(
            f"/repos/{OWNER}/{repo}/actions/runs/{run_id}"
        )

        if r.get("status")=="completed":
            if r.get("conclusion")!="success":
                raise RuntimeError(
                    f"TAZERIS COMPUTE failed: run={run_id} "
                    f"conclusion={r.get('conclusion')}"
                )
            break

        time.sleep(5)
    else:
        raise TimeoutError(
            f"TAZERIS COMPUTE timeout: run={run_id}"
        )

    arts=gh_compute(
        f"/repos/{OWNER}/{repo}/actions/runs/{run_id}/artifacts"
    ).get("artifacts",[])

    art=next(
        (a for a in arts if a.get("name")=="tazeris-result"),
        None
    )

    if not art:
        raise RuntimeError(
            f"TAZERIS COMPUTE artifact nerastas: run={run_id}"
        )

    # Artifact download endpoint grąžina redirect.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self,req,fp,code,msg,headers,newurl):
            return None

    opener=urllib.request.build_opener(NoRedirect)

    req=urllib.request.Request(
        f"https://api.github.com/repos/{OWNER}/{repo}/actions/artifacts/{art['id']}/zip",
        headers={
            "Authorization":f"Bearer {GH}",
            "Accept":"application/vnd.github+json",
            "X-GitHub-Api-Version":"2022-11-28"
        }
    )

    try:
        with opener.open(req,timeout=60) as r:
            blob=r.read()

    except urllib.error.HTTPError as e:
        if e.code not in (301,302,303,307,308):
            raise

        location=e.headers.get("Location")

        if not location:
            raise RuntimeError("TAZERIS COMPUTE artifact redirect nerastas")

        # Azure signed URL gauna užklausą be GitHub Authorization.
        with urllib.request.urlopen(location,timeout=120) as r:
            blob=r.read()

    with zipfile.ZipFile(io.BytesIO(blob)) as z:
        raw=z.read("result.txt").decode("utf-8","ignore")

    # llama-cli artifacte yra diagnostika; imame modelio atsakymo dalį.
    marker="> "+prompt
    pos=raw.rfind(marker)

    if pos >= 0:
        result=raw[pos+len(marker):]
    else:
        result=raw

    if "[ Prompt:" in result:
        result=result.split("[ Prompt:",1)[0]

    result=result.replace("Exiting...","").strip()

    if not result:
        raise RuntimeError(
            f"TAZERIS COMPUTE tuscias atsakymas: run={run_id}"
        )

    print(
        f"REMOTE_AI_OK run={run_id}",
        flush=True
    )

    return result

def ref_sha(branch):
    return gh(
        f"/repos/{OWNER}/{REPO}/git/ref/heads/{branch}"
    )["object"]["sha"]

def reset_branch():
    main_sha=ref_sha(MAIN)

    try:
        ref_sha(BRANCH)

        gh(
            f"/repos/{OWNER}/{REPO}/git/refs/heads/{BRANCH}",
            "PATCH",
            {"sha":main_sha,"force":True}
        )

    except urllib.error.HTTPError as e:
        if e.code not in (404,422):
            raise

        gh(
            f"/repos/{OWNER}/{REPO}/git/refs",
            "POST",
            {
                "ref":f"refs/heads/{BRANCH}",
                "sha":main_sha
            }
        )

def read_file(path):
    d=gh(
        f"/repos/{OWNER}/{REPO}/contents/{path}?ref={BRANCH}"
    )

    return (
        base64.b64decode(d["content"]).decode("utf-8"),
        d["sha"]
    )

def write_file(path,content):
    old,sha=read_file(path)

    if old.strip() == content.strip():
        return False

    gh(
        f"/repos/{OWNER}/{REPO}/contents/{path}",
        "PUT",
        {
            "message":f"TAZERIS V5 atomic: {path}",
            "content":base64.b64encode(
                content.encode("utf-8")
            ).decode("ascii"),
            "sha":sha,
            "branch":BRANCH
        }
    )

    return True

def bundle(paths,limit=4500):
    result=[]

    for p in paths:
        content,_=read_file(p)
        result.append(
            f"===== FILE: {p} =====\n{content[:limit]}"
        )

    return "\n\n".join(result)

def parse_files(raw,allowed):
    out={}

    matches=re.findall(
        r'<TAZERIS_FILE path="([^"]+)">\s*(.*?)\s*</TAZERIS_FILE>',
        raw,
        re.S
    )

    for path,content in matches:
        if path in allowed and content.strip():
            out[path]=content.strip()

    return out

def generate(task):
    context=bundle(task["files"])

    prompt=(
        f"ATOMIC TASK:\n{task['goal']}\n\n"
        f"REAL FILES:\n{context}\n\n"
        "Keep the change small. "
        "Do not redesign unrelated code. "
        "All ids/resources/dependencies referenced must already exist in supplied files "
        "or be created in another supplied changed file.\n\n"
        "Return only full changed files:\n"
        '<TAZERIS_FILE path="exact/path">FULL FILE CONTENT</TAZERIS_FILE>'
    )

    return parse_files(
        ai(prompt,900),
        task["files"]
    )

def workflows():
    return gh(
        f"/repos/{OWNER}/{REPO}/actions/workflows"
    ).get("workflows",[])

def trigger_build():
    ws=workflows()

    workflow=next(
        (
            x for x in ws
            if x.get("path","").endswith("build-apk.yml")
        ),
        None
    )

    if not workflow:
        workflow=next(
            (
                x for x in ws
                if "build" in x.get("name","").lower()
            ),
            None
        )

    if not workflow:
        raise RuntimeError("Build workflow nerastas")

    gh(
        f"/repos/{OWNER}/{REPO}/actions/workflows/{workflow['id']}/dispatches",
        "POST",
        {"ref":BRANCH}
    )

    time.sleep(4)

    return workflow["id"]

def wait_build(workflow_id):
    for _ in range(90):
        d=gh(
            f"/repos/{OWNER}/{REPO}/actions/workflows/"
            f"{workflow_id}/runs?branch={BRANCH}&per_page=1"
        )

        runs=d.get("workflow_runs",[])

        if runs:
            r=runs[0]

            if r.get("status")=="completed":
                return r.get("conclusion"),r.get("id")

        time.sleep(5)

    return "timeout",None

def failed_log(run_id):
    jobs=gh(
        f"/repos/{OWNER}/{REPO}/actions/runs/{run_id}/jobs"
    ).get("jobs",[])

    lines=[]

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None

    opener=urllib.request.build_opener(NoRedirect)

    for j in jobs:
        if j.get("conclusion")!="failure":
            continue

        api_url=(
            "https://api.github.com"
            f"/repos/{OWNER}/{REPO}/actions/jobs/{j['id']}/logs"
        )

        req=urllib.request.Request(
            api_url,
            headers={
                "Authorization":f"Bearer {GH}",
                "Accept":"application/vnd.github+json",
                "X-GitHub-Api-Version":"2022-11-28"
            }
        )

        try:
            with opener.open(req,timeout=60) as r:
                txt=r.read().decode("utf-8","ignore")

        except urllib.error.HTTPError as e:
            if e.code not in (301,302,303,307,308):
                raise

            location=e.headers.get("Location")
            if not location:
                raise RuntimeError("GitHub log redirect URL nerastas")

            # Svarbu: į pasirašytą logų saugyklos URL
            # GitHub Authorization antraštės NEPERSIUNČIAME.
            with urllib.request.urlopen(location,timeout=120) as r:
                txt=r.read().decode("utf-8","ignore")

        for line in txt.splitlines():
            if re.search(
                r"error:|unresolved reference|execution failed|"
                r"compilation error|resource linking failed|"
                r"failed with an exception|what went wrong|"
                r"plugin .* was not found|could not resolve|"
                r"could not find|build file .* line:",
                line,
                re.I
            ):
                lines.append(line)

    return "\n".join(lines[-80:])[-6500:]

def repair(task,log):
    mentioned=[
        p for p in task["files"]
        if p.split("/")[-1] in log or p in log
    ]

    if not mentioned:
        mentioned=task["files"]

    context=bundle(mentioned,3500)

    prompt=(
        "REPAIR ONLY THIS REAL COMPILATION FAILURE.\n"
        "Do not add features. Do not redesign.\n\n"
        f"COMPILER ERRORS:\n{log}\n\n"
        f"REAL FILES:\n{context}\n\n"
        "Return only corrected full files:\n"
        '<TAZERIS_FILE path="exact/path">FULL FILE CONTENT</TAZERIS_FILE>'
    )

    return parse_files(
        ai(prompt,700),
        mentioned
    )

def apply(changes):
    count=0

    for path,content in changes.items():
        if write_file(path,content):
            print("WRITE",path,flush=True)
            count+=1

    return count

def merge():
    return gh(
        f"/repos/{OWNER}/{REPO}/merges",
        "POST",
        {
            "base":MAIN,
            "head":BRANCH,
            "commit_message":
                "TAZERIS V5 verified atomic improvement"
        }
    )

def next_task(st):
    done=set(st["done"])

    available=[
        x for x in ROADMAP
        if x["id"] not in done
    ]

    if not available:
        st["done"]=[]
        available=ROADMAP[:]

    # Neleisti vienai blogai užduočiai deginti šimtų buildų.
    nonblocked=[
        x for x in available
        if st["failures"].get(x["id"],0) < 3
    ]

    if nonblocked:
        available=nonblocked
    else:
        # Po pilno rato leidžiame bandyti iš naujo.
        for x in available:
            st["failures"][x["id"]]=0

    available.sort(
        key=lambda x: st["failures"].get(x["id"],0)
    )

    return available[0]

def main():
    if not GH:
        raise SystemExit("STOP: GH_TOKEN NERA")

    started=time.time()
    max_runtime=int(os.environ.get("TAZERIS_MAX_RUNTIME_SECONDS","0") or 0)

    print("TAZERIS V5 FAST ATOMIC STARTED",flush=True)
    print(
        "OPENAI OFF | LOCAL QWEN ON | "
        "ATOMIC BUILD GATE ON",
        flush=True
    )

    st=state_load()

    while True:
        try:
            if max_runtime and time.time()-started >= max_runtime:
                state_save(st)
                print("GRACEFUL_RESTART cycles",st["cycles"],flush=True)
                break

            st["cycles"]+=1

            task=next_task(st)
            st["last"]=task["id"]

            state_save(st)

            print(
                "TASK",
                task["id"],
                "CYCLE",
                st["cycles"],
                flush=True
            )

            reset_branch()

            changes=generate(task)

            if not changes or apply(changes)==0:
                print("NO_CHANGE",task["id"],flush=True)

                st["failures"][task["id"]]=(
                    st["failures"].get(task["id"],0)+1
                )

                state_save(st)
                time.sleep(SLEEP)
                continue

            success=False

            for attempt in range(MAX_REPAIR+1):
                workflow_id=trigger_build()

                conclusion,run_id=wait_build(
                    workflow_id
                )

                print(
                    "BUILD",
                    attempt+1,
                    conclusion,
                    run_id,
                    flush=True
                )

                if conclusion=="success":
                    merge()

                    print(
                        "MERGED",
                        task["id"],
                        flush=True
                    )

                    if task["id"] not in st["done"]:
                        st["done"].append(task["id"])

                    st["failures"][task["id"]]=0

                    state_save(st)

                    success=True
                    break

                if not run_id or attempt>=MAX_REPAIR:
                    break

                log=failed_log(run_id)

                fixes=repair(task,log)

                if not fixes or apply(fixes)==0:
                    print(
                        "REPAIR_NO_CHANGE",
                        flush=True
                    )
                    break

            if not success:
                st["failures"][task["id"]]=(
                    st["failures"].get(task["id"],0)+1
                )

                state_save(st)

                print(
                    "MAIN_SAFE",
                    task["id"],
                    flush=True
                )

            reset_branch()
            time.sleep(SLEEP)

        except KeyboardInterrupt:
            break

        except Exception as e:
            print(
                "ERROR",
                type(e).__name__,
                str(e)[:500],
                flush=True
            )

            time.sleep(15)

if __name__=="__main__":
    main()
