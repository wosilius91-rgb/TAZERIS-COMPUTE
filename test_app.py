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
        self.assertIn("@huggingface/transformers@3.8.1", html)
        self.assertIn("onnx-community/Qwen2.5-1.5B-Instruct", html)
        self.assertIn("deterministicShortAnswer", html)
        self.assertIn("progress_callback:progress", html)
        self.assertIn("NIEKADA nepaversk", html)
        self.assertIn("isVisualRefinement", html)
        self.assertIn("promoLevel++", html)
        self.assertIn("async function imageClient()", html)
        self.assertNotIn("akhaliq/Qwen3-VL-4B-Instruct", html)
        self.assertIn("black-forest-labs/FLUX.1-schnell", html)
        self.assertIn('value="image"', html)
        self.assertIn("foto|fotograf", html)
        self.assertIn("client.predict(imageEndpoint,[", html)
        self.assertIn("englishPrompt,", html)
        self.assertIn("1024,", html)
        self.assertIn("savo gebėjimą", html)
        self.assertIn("1–5 sakiniais", html)
        self.assertIn("isTazerisPromo", html)
        self.assertIn("localTazerisPromo", html)
        self.assertIn('canvas.toDataURL("image/png")', html)
        self.assertIn("be GPU limito", html)
        self.assertIn("renderLiteMarkdown", html)

if __name__ == "__main__":
    unittest.main()
