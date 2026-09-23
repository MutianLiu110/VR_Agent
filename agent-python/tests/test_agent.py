import asyncio
import os
import unittest
from unittest.mock import patch

import httpx

from app.main import app, extract_answer


class AgentContractTest(unittest.TestCase):
    def request(self, stage="SCENE_SAFETY", question="What next?", messages=None):
        async def send():
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://agent") as client:
                return await client.post(
                    "/internal/respond",
                    json={
                        "sessionId": "test-session",
                        "stage": stage,
                        "messages": messages or [{"role": "user", "content": question}],
                        "answerLength": "short",
                    },
                )

        return asyncio.run(send())

    def test_no_key_returns_java_compatible_timeout_for_every_stage(self):
        with patch.dict(os.environ, {"DEEPSEEK_API_KEY": "", "DEEPSEEK_MODEL": ""}):
            for stage in ("SCENE_SAFETY", "INITIAL_ASSESSMENT", "COMPLETE"):
                response = self.request(stage=stage)
                self.assertEqual(504, response.status_code)
                self.assertEqual("timeout", response.json()["mode"])
                self.assertIsNone(response.json()["suggestedAdjustment"])

    def test_unknown_stage_is_rejected(self):
        response = self.request(stage="UNAPPROVED")
        self.assertEqual(400, response.status_code)

    def test_valid_model_response_preserves_java_contract(self):
        captured = {}

        class FakeProvider:
            async def __aenter__(self):
                return self

            async def __aexit__(self, *_):
                pass

            async def post(self, *_args, **kwargs):
                captured.update(kwargs["json"])
                return httpx.Response(200, json={"output": [{"type": "message", "content": [
                    {"type": "output_text", "text": "Follow the instructor checklist."},
                ]}]})

        with patch.dict(os.environ, {"DEEPSEEK_API_KEY": "test-only", "DEEPSEEK_MODEL": "deepseek-flash"}):
            with patch("app.main.provider_client", return_value=FakeProvider()):
                response = self.request(messages=[
                    {"role": "user", "content": "My name is Alex."},
                    {"role": "assistant", "content": "Hello Alex."},
                    {"role": "user", "content": "The sound is too loud. What is my name?"},
                ])
        self.assertEqual(200, response.status_code)
        self.assertEqual("deepseek", response.json()["mode"])
        self.assertEqual("Follow the instructor checklist.", response.json()["reply"])
        self.assertEqual("REDUCE_BACKGROUND_SOUND", response.json()["suggestedAdjustment"])
        self.assertEqual(3, len(captured["input"]))
        self.assertEqual("Hello Alex.", captured["input"][1]["content"])

    def test_provider_timeout_returns_504(self):
        class TimeoutProvider:
            async def __aenter__(self):
                return self

            async def __aexit__(self, *_):
                pass

            async def post(self, *_args, **_kwargs):
                raise httpx.ReadTimeout("test timeout")

        with patch.dict(os.environ, {"DEEPSEEK_API_KEY": "test-only", "DEEPSEEK_MODEL": "deepseek-flash"}):
            with patch("app.main.provider_client", return_value=TimeoutProvider()):
                response = self.request()
        self.assertEqual(504, response.status_code)
        self.assertEqual("timeout", response.json()["mode"])
        self.assertIsNone(response.json()["suggestedAdjustment"])

    def test_extracts_only_output_text(self):
        payload = {"output": [{"type": "message", "content": [
            {"type": "output_text", "text": "Hello"},
            {"type": "refusal", "text": "ignored"},
        ]}]}
        self.assertEqual("Hello", extract_answer(payload))

    def test_last_message_must_be_user(self):
        response = self.request(messages=[{"role": "assistant", "content": "Hello"}])
        self.assertEqual(400, response.status_code)


if __name__ == "__main__":
    unittest.main()
