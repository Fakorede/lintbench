"""
generate/providers.py
---------------------
LLM API client implementations.

Supported providers:
  openai      -- OpenAI Chat Completions (gpt-*, o1, o3, o4)
  anthropic   -- Anthropic Messages API (claude-*)
  google      -- Google Generative AI (gemini-*)
  openrouter  -- OpenRouter (any model via openrouter.ai, or provider/model syntax)

Auto-detection via detect_provider(); override with the --provider CLI flag.

Each call_* function returns (text, usage) where usage is:
  {"input_tokens": int, "output_tokens": int}
"""

import os
from typing import Optional


def detect_provider(model: str, api_base: Optional[str] = None) -> str:
    """Infer provider from model name and optional api_base URL."""
    if api_base and "openrouter.ai" in api_base:
        return "openrouter"
    # OpenRouter uses "provider/model" naming (e.g. openai/gpt-4o, meta-llama/llama-3.3-70b)
    if "/" in model:
        return "openrouter"
    m = model.lower()
    if any(x in m for x in ("gpt", "o1", "o3", "o4")):
        return "openai"
    if "claude" in m:
        return "anthropic"
    if "gemini" in m:
        return "google"
    return "openai"  # fallback for custom OpenAI-compatible endpoints


def call_openai(
    model: str,
    system: str,
    user: str,
    temperature: float,
    max_tokens: int,
    api_base: Optional[str] = None,
) -> tuple[str, dict]:
    try:
        import openai
    except ImportError:
        raise SystemExit("openai package not installed. Run: pip install openai")

    client = openai.OpenAI(
        api_key=os.environ.get("OPENAI_API_KEY", ""),
        base_url=api_base,
    )
    kwargs: dict = dict(
        model=model,
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        max_tokens=max_tokens,
    )
    # o1/o3/o4 reasoning models: no system role, no temperature, use max_completion_tokens
    if model.startswith(("o1", "o3", "o4")):
        kwargs["messages"] = [{"role": "user", "content": system + "\n\n" + user}]
        kwargs.pop("max_tokens", None)
        kwargs["max_completion_tokens"] = max_tokens
    else:
        kwargs["temperature"] = temperature

    resp = client.chat.completions.create(**kwargs)
    text = resp.choices[0].message.content or ""
    usage = {
        "input_tokens":  resp.usage.prompt_tokens,
        "output_tokens": resp.usage.completion_tokens,
    }
    return text, usage


def call_anthropic(
    model: str,
    system: str,
    user: str,
    temperature: float,
    max_tokens: int,
) -> tuple[str, dict]:
    try:
        import anthropic
    except ImportError:
        raise SystemExit("anthropic package not installed. Run: pip install anthropic")

    client = anthropic.Anthropic(api_key=os.environ.get("ANTHROPIC_API_KEY", ""))
    resp = client.messages.create(
        model=model,
        max_tokens=max_tokens,
        temperature=temperature,
        system=system,
        messages=[{"role": "user", "content": user}],
    )
    text = resp.content[0].text
    usage = {
        "input_tokens":  resp.usage.input_tokens,
        "output_tokens": resp.usage.output_tokens,
    }
    return text, usage


def call_google(
    model: str,
    system: str,
    user: str,
    temperature: float,
    max_tokens: int,
) -> tuple[str, dict]:
    try:
        import google.generativeai as genai
    except ImportError:
        raise SystemExit(
            "google-generativeai package not installed. "
            "Run: pip install google-generativeai"
        )

    genai.configure(api_key=os.environ.get("GOOGLE_API_KEY", ""))
    gmodel = genai.GenerativeModel(model_name=model, system_instruction=system)
    resp = gmodel.generate_content(
        user,
        generation_config=genai.types.GenerationConfig(
            temperature=temperature,
            max_output_tokens=max_tokens,
        ),
    )
    text = resp.text
    usage = {
        "input_tokens":  resp.usage_metadata.prompt_token_count,
        "output_tokens": resp.usage_metadata.candidates_token_count,
    }
    return text, usage


def call_openrouter(
    model: str,
    system: str,
    user: str,
    temperature: float,
    max_tokens: int,
) -> tuple[str, dict]:
    """
    Call any model via OpenRouter's OpenAI-compatible API.

    Requires OPENROUTER_API_KEY. Model names use provider/model syntax:
      openai/gpt-4o, anthropic/claude-opus-4-5, google/gemini-2.5-pro,
      meta-llama/llama-3.3-70b-instruct, mistralai/mistral-large, …

    See https://openrouter.ai/models for the full list.
    """
    try:
        import openai
    except ImportError:
        raise SystemExit("openai package not installed. Run: pip install openai")

    client = openai.OpenAI(
        api_key=os.environ.get("OPENROUTER_API_KEY", ""),
        base_url="https://openrouter.ai/api/v1",
        default_headers={
            "HTTP-Referer": "https://github.com/lintbench/lintbench",
            "X-Title": "LintBench",
        },
    )
    resp = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        temperature=temperature,
        max_tokens=max_tokens,
    )
    text = resp.choices[0].message.content or ""
    usage = {
        "input_tokens":  resp.usage.prompt_tokens,
        "output_tokens": resp.usage.completion_tokens,
    }
    return text, usage


def generate_sample(
    model: str,
    provider: str,
    system: str,
    user: str,
    temperature: float,
    max_tokens: int,
    api_base: Optional[str] = None,
) -> tuple[str, dict]:
    """Dispatch to the right API client and return (text, usage)."""
    if provider == "openai":
        return call_openai(model, system, user, temperature, max_tokens, api_base)
    if provider == "anthropic":
        return call_anthropic(model, system, user, temperature, max_tokens)
    if provider == "google":
        return call_google(model, system, user, temperature, max_tokens)
    if provider == "openrouter":
        return call_openrouter(model, system, user, temperature, max_tokens)
    raise ValueError(f"Unknown provider: {provider}")
