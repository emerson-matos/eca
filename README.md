<a href="https://eca.dev"><img src="images/logo.png" width="110" align="right"></a>

[![GitHub Release](https://img.shields.io/github/v/release/editor-code-assistant/eca?display_name=release&style=flat-square)](https://github.com/editor-code-assistant/eca/releases/latest)
<a href="https://github.com/editor-code-assistant/eca/stargazers"><img alt="GitHub Stars" title="Total number of GitHub stars the ECA project has received"
src="https://img.shields.io/github/stars/editor-code-assistant/eca?style=flat-square&logo=github&color=f1c40f&labelColor=555555"/></a>
[![Downloads](https://img.shields.io/github/downloads/editor-code-assistant/eca/total.svg?style=flat-square)](https://github.com/editor-code-assistant/eca/releases/latest)
[![Chat community](https://img.shields.io/badge/Slack-chat-blue?style=flat-square)](https://clojurians.slack.com/archives/C093426FPUG)

# ECA (Editor Code Assistant)

| Demo                                                                                                                             |
|:----------------------------------------------------------------------------------------------------------------------------------:|
| [eca-emacs](https://github.com/editor-code-assistant/eca-emacs)<br/><img src="https://raw.githubusercontent.com/editor-code-assistant/eca-emacs/master/demo.gif" width="640" /> |
| [eca-vscode](https://github.com/editor-code-assistant/eca-vscode)<br/><img src="https://raw.githubusercontent.com/editor-code-assistant/eca-vscode/master/demo.gif" width="640" /> |
| [eca-intellij](https://github.com/editor-code-assistant/eca-intellij)<br/><img src="https://raw.githubusercontent.com/editor-code-assistant/eca-intellij/master/demo.gif" width="640" /> |
 
<hr>
<p align="center">
  <a href="https://eca.dev/installation"><strong>installation</strong></a> •
  <a href="https://eca.dev/features"><strong>features</strong></a> •
  <a href="https://eca.dev/configuration"><strong>configuration</strong></a> •
  <a href="https://eca.dev/models"><strong>models</strong></a> •
  <a href="https://eca.dev/protocol"><strong>protocol</strong></a>
  <a href="https://eca.dev/troubleshooting"><strong>troubleshooting</strong></a>
</p>
<hr>

- :page_facing_up: **Editor-agnostic**: protocol for any editor to integrate.
- :gear: **Single configuration**: Configure eca making it work the same in any editor via global or local configs.
- :loop: **Chat** interface: ask questions, review code, work together to code.
- :coffee: **Agentic**: let LLM work as an agent with its native tools and MCPs you can configure.
- :syringe: **Context**: support: giving more details about your code to the LLM, including MCP resources and prompts.
- :rocket: **Multi models**: OpenAI, Anthropic, Copilot, Ollama local models, and custom user config models.

## Rationale 

<img src="images/rationale.jpg" width="500">

A Free and OpenSource editor-agnostic tool that aims to easily link LLMs <-> Editors, giving the best UX possible for AI pair programming using a well-defined protocol. The server is written in Clojure and heavily inspired by the [LSP protocol](https://microsoft.github.io/language-server-protocol/) which is a success case for this kind of integration.

The protocol makes easier for other editors integrate and having a server in the middle helps adding more features quickly, some examples:
- Tool call management
- Multiple LLM interaction 
- Telemetry of features usage
- Single way to configure for any editor
- Same UX, easy to onboard people and teams. 

With the LLMs models race, the differences between them tend to be irrelevant in the future, but UX on how to edit code or plan changes is something that will exist, ECA helps editors focus on that.

## Getting started

### 1. Install the editor plugin

Install the plugin for your editor and ECA server will be downloaded and started automatically:

- [Emacs](https://github.com/editor-code-assistant/eca-emacs)
- [VsCode](https://github.com/editor-code-assistant/eca-vscode)
- [Vim](https://github.com/editor-code-assistant/eca-nvim)
- Intellij: [WIP](https://github.com/editor-code-assistant/eca-intellij), help welcome

### 2. Set up your first model

To use ECA, you need to configure at least one model with your API key. See the [Models documentation](https://eca.dev/models#adding-and-configuring-models) for detailed instructions on:

- Setting up API keys for OpenAI, Anthropic, or Ollama
- Adding and customizing models
- Configuring custom providers

**Quick start**: Create a `.eca/config.json` file in your project root with your API key:

```json
{
  "providers": {
      "openai": {"key": "your-openai-api-key-here"},
      "anthropic": {"key": "your-anthropic-api-key-here"}
  }
}
```

**Note**: For other providers or custom models, see the [custom providers documentation](https://eca.dev/models#setting-up-a-custom-provider).

### 3. Start chatting

Once your model is configured, you can start using ECA's chat interface in your editor to ask questions, review code, and work together on your project.

Type `/init` to ask ECA to create a [AGENTS.md](http://agents.md) file which will help ECA on next iterations have good context about your project standards.

## How it works

Editors spawn the server via `eca server` and communicate via stdin/stdout, similar to LSPs. Supported editors already download latest server on start and require no extra configuration.

## Roadmap

Check the planned work [here](https://github.com/orgs/editor-code-assistant/projects/1).

## Contributing

Contributions are very welcome, please open an issue for discussion or a pull request.
For developer details, check [this doc](https://eca.dev/development).

## Support the project 💖

Consider [sponsoring the project](https://github.com/sponsors/ericdallo) to help grow faster, the support helps to keep the project going, being updated and maintained!
