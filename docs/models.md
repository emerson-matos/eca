# Models

All providers and models are configured under `providers` config.

Models capabilities and configurations are retrieved from [models.dev](https://models.dev) API.

## Built-in providers and capabilities

| model                         | tools (MCP) | reasoning / thinking | prompt caching | web_search |
|-------------------------------|-------------|----------------------|----------------|------------|
| OpenAI                        | √           | √                    | √              | √          |
| Anthropic (Also subscription) | √           | √                    | √              | √          |
| Github Copilot                | √           | √                    | √              | X          |
| Ollama local models           | √           | √                    | X              | X          |


### Config

Built-in providers have already base initial `providers` configs, so you can change to add models or set its key/url.

For more details, check the [config schema](./configuration.md#all-configs).

Example:

`~/.config/eca/config.json`
```javascript
{
  "providers": {
    "openai": {
      "key": "your-openai-key-here", // configuring a key
      "models": { 
        "o1": {} // adding models to a built-in provider
        "o3": {
          "extraPayload": { // adding to the payload sent to LLM
            "temperature": 0.5
          }
        }
      }
    } 
  }
}
```

**Environment Variables**: You can also set API keys using environment variables following `"<PROVIDER>_API_KEY"`, examples:

- `OPENAI_API_KEY` for OpenAI
- `ANTHROPIC_API_KEY` for Anthropic

## Custom providers

ECA allows you to configure custom LLM providers that follow API schemas similar to OpenAI or Anthropic. This is useful when you want to use:

- Self-hosted LLM servers (like LiteLLM)
- Custom company LLM endpoints
- Additional cloud providers not natively supported

You just need to add your provider to `providers` and make sure add the required fields

Schema:

| Option                        | Type   | Description                                                                     | Required |
|-------------------------------|--------|---------------------------------------------------------------------------------|----------|
| `api`                         | string | The API schema to use (`"openai-responses"`, `"openai-chat"`, or `"anthropic"`) | Yes      |
| `urlEnv`                      | string | Environment variable name containing the API URL                                | No*      |
| `url`                         | string | Direct API URL (use instead of `urlEnv`)                                        | No*      |
| `keyEnv`                      | string | Environment variable name containing the API key                                | No*      |
| `key`                         | string | Direct API key (use instead of `keyEnv`)                                        | No*      |
| `models`                      | map    | Key: model name, value: its config                                              | Yes      |
| `models <model> extraPayload` | map    | Extra payload sent in body to LLM                                               | No       |

_* url and key will be search as env `<provider>_API_URL` / `<provider>_API_KEY`, but require config or to be found to work._

Example:

`~/.config/eca/config.json`
```javascript
{
  "providers": {
    "my-company": {
      "api": "openai-chat",
      "urlEnv": "MY_COMPANY_API_URL", // or "url"
      "keyEnv": "MY_COMPANY_API_KEY", // or "key"
      "models": {
        "gpt-5": {},
        "deepseek-r1": {}
       }
    }
  }
}
```

### API Types

When configuring custom providers, choose the appropriate API type:

- **`anthropic`**: Anthropic's native API for Claude models.
- **`openai-responses`**: OpenAI's new responses API endpoint (`/v1/responses`). Best for OpenAI models with enhanced features like reasoning and web search.
- **`openai-chat`**: Standard OpenAI Chat Completions API (`/v1/chat/completions`). Use this for most third-party providers:
    - OpenRouter
    - DeepSeek
    - Together AI
    - Groq
    - Local LiteLLM servers
    - Any OpenAI-compatible provider

Most third-party providers use the `openai-chat` API for compatibility with existing tools and libraries.

## Providers examples

=== "Anthropic"
    
    1. Login to Anthropic via the chat command `/login anthropic`.
    2. Type the chosen method
    3. Authenticate in your browser, copy the code.
    4. Paste and send the code and done!

=== "Github Copilot"
    
    1. Login to Github copilot via the chat command `/login github-copilot`.
    2. Authenticate in Github in your browser with the given code.
    3. Type anything in the chat to continue and done!
    
    _Tip: check [Your Copilot plan](https://github.com/settings/copilot/features) to enable models to your account._

=== "LiteLLM"

    ```javascript
    {
      "providers": {
        "litellm": {
          "api": "openai-responses",
          "url": "https://litellm.my-company.com", // or "urlEnv"
          "key": "your-api-key", // or "keyEnv"
          "models": {
            "gpt-5": {},
            "deepseek-r1": {}
           }
        }
      }
    }
    ```

=== "OpenRouter"

    [OpenRouter](https://openrouter.ai) provides access to many models through a unified API:
    
    ```javascript
    {
      "providers": {
        "openrouter": {
          "api": "openai-chat",
          "url": "https://openrouter.ai/api/v1", // or "urlEnv"
          "key": "your-api-key", // or "keyEnv"
          "models": {
            "anthropic/claude-3.5-sonnet": {},
            "openai/gpt-4-turbo": {},
            "meta-llama/llama-3.1-405b": {}
           }
        }
      }
    }
    ```

=== "DeepSeek"

    [DeepSeek](https://deepseek.com) offers powerful reasoning and coding models:
    
    ```javascript
    {
      "providers": {
        "openrouter": {
          "api": "openai-chat",
          "url": "https://api.deepseek.com", // or "urlEnv"
          "key": "your-api-key", // or "keyEnv"
          "models": {
            "deepseek-chat": {},
            "deepseek-coder": {},
            "deepseek-reasoner": {}
           }
        }
      }
    }
    ```

=== "Azure OpenAI"

    ```javascript
    {
      "providers": {
        "azure": {
          "api": "openai-responses",
          "url": "https://your-resource-name.openai.azure.com", // or "urlEnv"
          "key": "your-api-key", // or "keyEnv"
          "completionUrlRelativePath": "/openai/responses?api-version=2025-04-01-preview",
          "models": {
            "gpt-5": {},
           }
        }
      }
    }
    ```
