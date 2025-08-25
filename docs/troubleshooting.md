# Troubleshooting

## Server logs

ECA works with clients (editors) sending and receiving messages to server, a process, you can start server with `--log-level debug` or `--verbose` which should log helpful information to `stderr` buffer like what is being sent to LLMs or what ECA is responding to editors, all supported editors have options to set the __server args___ to help with that.

## Doctor command

`/doctor` command should log useful information to debug model used, server version, env vars and more.

## Missing env vars

Some editors are started with empty shell especially on MacOS, you can check if the env vars are available via `/doctor`.

One way to workaround that is to start the editor from your terminal.

## Ask for help

You can ask for help via chat [here](https://clojurians.slack.com/archives/C093426FPUG)
