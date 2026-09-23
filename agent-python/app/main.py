"""Python Agent service behind the Java backend.
Unity never calls this service directly.
"""

import os
from typing import Literal

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel


# DeepSeek Responses API
RESPONSES_URL = "https://api.deepseek.com/responses"


STAGE_NOTES = {
    "SCENE_SAFETY": (
        "This is a simulated scene-safety stage. Ask the learner to follow "
        "the instructor-approved scene checklist before advancing."
    ),

    "INITIAL_ASSESSMENT": (
        "This is a simulated initial-assessment stage. Ask the learner to "
        "follow the instructor-approved assessment checklist. "
        "Do not invent clinical steps."
    ),

    "COMPLETE": (
        "The short training scenario is complete. Invite the learner to review "
        "the instructor-approved feedback."
    ),
}


app = FastAPI(title="VR Agent Service")


# =========================
# Request / Response Models
# =========================

class ChatMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str


class AgentRequest(BaseModel):
    sessionId: str
    stage: str
    answerLength: str | None = "short"
    messages: list[ChatMessage]


class AgentReply(BaseModel):
    reply: str
    suggestedAdjustment: str | None = None
    mode: str


# =========================
# Helper Functions
# =========================

def timeout_reply(message: str) -> JSONResponse:
    return JSONResponse(
        status_code=504,
        content=AgentReply(
            reply=message,
            mode="timeout"
        ).model_dump(),
    )


def answer_instructions(
    answer_length: str | None,
    stage: str,
    stage_note: str
) -> str:

    if (answer_length or "").lower() == "detailed":
        guidance = "Give a concise explanation with a little context."
    else:
        guidance = "Keep the answer to one or two short sentences."

    return (
        "You are a supportive virtual character in an educational VR prototype. "
        "Speak clearly and without stereotypes. "
        "Answer only about the provided simulated stage. "

        "The stage note is the only approved scenario guidance. "
        "If it is insufficient, say so and refer the learner to the "
        "instructor-approved checklist. "

        "Do not give real-world medical advice. "
        "Never claim to have changed the VR environment. "

        f"Current simulation stage: {stage}. "
        f"Approved stage note: {stage_note} "

        + guidance
    )


def extract_answer(payload: dict) -> str:
    """
    Extract visible assistant text from DeepSeek Responses API response.
    """

    parts = []

    for item in payload.get("output", []):

        # DeepSeek may also return reasoning items.
        # We only want the final assistant message.
        if item.get("type") != "message":
            continue

        for content in item.get("content", []):

            if content.get("type") == "output_text":
                text = content.get("text", "")

                if text:
                    parts.append(text)

    return "".join(parts).strip()


def provider_client() -> httpx.AsyncClient:
    return httpx.AsyncClient(
        timeout=httpx.Timeout(
            25.0,
            connect=5.0
        )
    )


# =========================
# API Endpoints
# =========================

@app.get("/health")
async def health() -> dict:
    return {
        "status": "ok",
        "service": "agent"
    }


@app.post(
    "/internal/respond",
    response_model=AgentReply
)
async def respond(
    request: AgentRequest
) -> AgentReply | JSONResponse:

    # -------------------------
    # Validate request
    # -------------------------

    if (
        not request.sessionId.strip()
        or not request.stage.strip()
        or not request.messages
    ):
        raise HTTPException(
            status_code=400,
            detail="sessionId, stage and messages are required"
        )

    if any(
        not message.content.strip()
        for message in request.messages
    ):
        raise HTTPException(
            status_code=400,
            detail="message content cannot be blank"
        )

    if request.messages[-1].role != "user":
        raise HTTPException(
            status_code=400,
            detail="the last message must be from the user"
        )

    # -------------------------
    # Validate scenario stage
    # -------------------------

    note = STAGE_NOTES.get(request.stage)

    if note is None:
        raise HTTPException(
            status_code=400,
            detail="Unknown scenario stage"
        )

    # -------------------------
    # DeepSeek configuration
    # -------------------------

    api_key = os.getenv(
        "DEEPSEEK_API_KEY",
        ""
    ).strip()

    if not api_key:
        return timeout_reply(
            "Agent unavailable in demo mode: "
            "DEEPSEEK_API_KEY is not configured."
        )

    model = os.getenv(
        "DEEPSEEK_MODEL",
        "deepseek-flash"
    ).strip()

    # -------------------------
    # Build DeepSeek request
    # -------------------------

    body = {
        "model": model,

        "instructions": answer_instructions(
            request.answerLength,
            request.stage,
            note
        ),

        "input": [
            message.model_dump()
            for message in request.messages
        ],
    }

    # -------------------------
    # Call DeepSeek API
    # -------------------------

    try:

        async with provider_client() as client:

            response = await client.post(
                RESPONSES_URL,

                headers={
                    "Authorization": f"Bearer {api_key}",
                    "Content-Type": "application/json",
                },

                json=body,
            )

    except httpx.TimeoutException:

        return timeout_reply(
            "Agent request timed out. Please try again."
        )

    except httpx.RequestError as exc:

        raise HTTPException(
            status_code=502,
            detail="DeepSeek API is unavailable"
        ) from exc

    # -------------------------
    # Handle provider errors
    # -------------------------

    if response.status_code != 200:

        # Useful while developing / demonstrating
        print(
            "DeepSeek error:",
            response.status_code,
            response.text
        )

        raise HTTPException(
            status_code=502,
            detail=(
                "DeepSeek returned HTTP "
                f"{response.status_code}"
            )
        )

    # -------------------------
    # Parse DeepSeek response
    # -------------------------

    try:

        payload = response.json()

        answer = extract_answer(payload)

    except (
        ValueError,
        TypeError,
        AttributeError
    ) as exc:

        raise HTTPException(
            status_code=502,
            detail="DeepSeek returned invalid JSON"
        ) from exc

    if not answer:

        raise HTTPException(
            status_code=502,
            detail="DeepSeek returned no text"
        )

    # -------------------------
    # Simple environment hint
    # -------------------------

    question = request.messages[-1].content.casefold()

    suggestion = (
        "REDUCE_BACKGROUND_SOUND"
        if any(
            word in question
            for word in (
                "noise",
                "sound",
                "吵",
                "声音",
            )
        )
        else None
    )

    # -------------------------
    # Return to Java backend
    # -------------------------

    return AgentReply(
        reply=answer,
        suggestedAdjustment=suggestion,
        mode="deepseek",
    )