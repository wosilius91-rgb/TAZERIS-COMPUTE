import unittest
from pathlib import Path

class StaticPageTests(unittest.TestCase):
    def test_accountless_ai_page(self):
        html = Path("index.html").read_text(encoding="utf-8")
        low = html.casefold()
        self.assertIn("tazeris ai", low)
        self.assertNotIn("puter.com", low)
        self.assertNotIn("openrouter", low)
        self.assertIn("be registracijos", low)
        self.assertIn("@huggingface/transformers@3.8.1", html)
        self.assertIn("onnx-community/Qwen2.5-0.5B-Instruct", html)
        self.assertIn("image.pollinations.ai", html)

if __name__ == "__main__":
    unittest.main()
