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

# ---------------------------------------------------------------------------
# Pricing — USD per token (OpenRouter format: price = $/token)
# Update when models are added or repriced.
# ---------------------------------------------------------------------------
_PRICING: dict[str, tuple[float, float]] = {
    # (input $/token, output $/token)
    "anthropic/claude-sonnet-4.6":  (0.000003,   0.000015),
    "openai/gpt-5.5":               (0.000005,   0.00003),
    "google/gemini-3.5-flash":      (0.0000015,  0.000009),
    # Additional models
    "anthropic/claude-sonnet-4.5":  (0.000003,   0.000015),
    "anthropic/claude-sonnet-4":    (0.000003,   0.000015),
    "openai/gpt-4.1":               (0.000002,   0.000008),
    "openai/gpt-4o":                (0.0000025,  0.00001),
    "openai/gpt-5":                 (0.00000125, 0.00001),
    "openai/gpt-5.1":               (0.00000125, 0.00001),
    "openai/gpt-5.4":               (0.0000025,  0.000015),
}


def compute_cost(model: str, input_tokens: int, output_tokens: int) -> float | None:
    """Return estimated cost in USD, or None if model is not in the price table."""
    prices = _PRICING.get(model)
    if prices is None:
        return None
    in_price, out_price = prices
    return in_price * input_tokens + out_price * output_tokens


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
) -> tuple[str, dict, str | None]:
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

    # Content may include thinking blocks (extended thinking) alongside text blocks
    text      = ""
    reasoning = None
    for block in resp.content:
        if block.type == "text":
            text = block.text
        elif block.type == "thinking":
            reasoning = block.thinking

    usage: dict = {
        "input_tokens":  resp.usage.input_tokens,
        "output_tokens": resp.usage.output_tokens,
    }
    # Reasoning tokens are bundled into output_tokens by Anthropic; extract if exposed
    rt = getattr(resp.usage, "reasoning_tokens", None) or (
        (getattr(resp.usage, "model_extra", None) or {}).get("reasoning_tokens")
    )
    if rt:
        usage["reasoning_tokens"] = rt

    return text, usage, reasoning


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
    thinking_budget: int | None = None,
) -> tuple[str, dict, str | None]:
    """
    Call any model via OpenRouter's OpenAI-compatible API.

    Requires OPENROUTER_API_KEY. Model names use provider/model syntax:
      openai/gpt-4o, anthropic/claude-opus-4-5, google/gemini-2.5-pro,
      meta-llama/llama-3.3-70b-instruct, mistralai/mistral-large, …

    thinking_budget: if set, enables extended thinking for anthropic/* models
      (budget_tokens = thinking_budget). Anthropic requires temperature=1 when
      thinking is enabled — this is enforced automatically.
      For openai/* reasoning models, thinking is always on; this param is ignored.

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

    kwargs: dict = dict(
        model=model,
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        temperature=temperature,
        max_tokens=max_tokens,
    )

    if thinking_budget:
        # OpenRouter unified reasoning parameter — works for Claude, GPT, Gemini.
        # Anthropic models additionally require temperature=1 when thinking is enabled.
        kwargs["extra_body"] = {"reasoning": {"max_tokens": thinking_budget}}
        if model.startswith("anthropic/"):
            kwargs["temperature"] = 1

    resp = client.chat.completions.create(**kwargs)

    msg = resp.choices[0].message
    text = msg.content or ""
    if not text:
        finish = resp.choices[0].finish_reason
        raise RuntimeError(
            f"Empty response from {model} (finish_reason={finish!r}). "
            "Try increasing --max-tokens."
        )

    # Reasoning/thinking content — try direct attribute first, then model_extra
    msg_extra = getattr(msg, "model_extra", None) or {}
    reasoning: str | None = (
        getattr(msg, "reasoning_content", None)
        or msg_extra.get("reasoning_content")
        or msg_extra.get("reasoning")
        or msg_extra.get("thinking")
        or None
    )

    usage: dict = {
        "input_tokens":  resp.usage.prompt_tokens,
        "output_tokens": resp.usage.completion_tokens,
    }
    # Reasoning token count — check completion_tokens_details and usage.model_extra
    details = getattr(resp.usage, "completion_tokens_details", None)
    usage_extra = getattr(resp.usage, "model_extra", None) or {}
    rt = (
        (getattr(details, "reasoning_tokens", None) if details else None)
        or usage_extra.get("reasoning_tokens")
        or usage_extra.get("thinking_tokens")
        or None
    )
    if rt:
        usage["reasoning_tokens"] = rt

    return text, usage, reasoning


def generate_sample(
    model: str,
    provider: str,
    system: str,
    user: str,
    temperature: float,
    max_tokens: int,
    api_base: Optional[str] = None,
    thinking_budget: int | None = None,
) -> tuple[str, dict, str | None]:
    """Dispatch to the right API client and return (text, usage, reasoning).

    reasoning is the model's chain-of-thought content if exposed by the API
    (currently OpenRouter only), otherwise None.
    """
    if provider == "openai":
        text, usage = call_openai(model, system, user, temperature, max_tokens, api_base)
        return text, usage, None
    if provider == "anthropic":
        return call_anthropic(model, system, user, temperature, max_tokens)
    if provider == "google":
        text, usage = call_google(model, system, user, temperature, max_tokens)
        return text, usage, None
    if provider == "openrouter":
        return call_openrouter(model, system, user, temperature, max_tokens, thinking_budget)
    raise ValueError(f"Unknown provider: {provider}")
