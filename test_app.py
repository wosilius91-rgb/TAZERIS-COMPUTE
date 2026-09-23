import unittest
from pathlib import Path

class StaticPageTests(unittest.TestCase):
    def test_accountless_remote_ai_page(self):
        html = Path("index.html").read_text(encoding="utf-8")
        low = html.casefold()
        self.assertIn("tazeris ai", low)
        self.assertNotIn("puter.com", low)
        self.assertNotIn("openrouter", low)
        self.assertNotIn("pollinations", low)
        self.assertNotIn("qwen2.5-0.5b", low)
        self.assertIn("@gradio/client", html)
        self.assertIn("akhaliq/Qwen3-VL-4B-Instruct", html)
        self.assertIn("black-forest-labs/FLUX.1-schnell", html)
        self.assertIn('value="image"', html)
        self.assertIn("foto|fotograf", html)
        self.assertIn("client.predict(imageEndpoint,[", html)
        self.assertIn("englishPrompt,", html)
        self.assertIn("1024,", html)
        self.assertIn("SAVO gebėjimą", html)
        self.assertIn("1–5 sakiniais", html)
        self.assertIn("isTazerisPromo", html)
        self.assertIn("localTazerisPromo", html)
        self.assertIn('canvas.toDataURL("image/png")', html)
        self.assertIn("be GPU limito", html)
        self.assertIn("renderLiteMarkdown", html)

if __name__ == "__main__":
    unittest.main()
