# Omumu CLI

Command-line interface for the [Omumu](https://omumu.com) customer education platform. Designed for humans and AI agents.

## Install

```bash
brew tap omumuas/omumu
brew install omumu
```

## Quick start

```bash
# Log in (opens browser)
omumu login --url https://yoursite.myomumu.com

# Check connection
omumu status

# List your courses
omumu course list
```

## What it does

Manage your Omumu site from the terminal — courses, modules, lessons, quizzes, email sequences, pages, opt-in forms, and more. 56 commands available.

```
$ omumu course list
+---------------+-----------------------------------+---------+---------+
| ID            | Title                             | Modules | Lessons |
+---------------+-----------------------------------+---------+---------+
| BAZBT4GA9GRGG | Your First Customer Education Win | 5       | 5       |
| BB82G1R1MT05C | Stop Answering the Same Questions | 1       | 4       |
+---------------+-----------------------------------+---------+---------+
```

## For AI agents and scripts

Every command supports `--json` for machine-readable output:

```bash
omumu course list --json
```

Discover all available commands and their parameters:

```bash
omumu schema
```

Environment variables for non-interactive use:

| Variable | Description |
|---|---|
| `OMUMU_API_KEY` | Override API key |
| `OMUMU_URL` | Override site URL |
| `OMUMU_OUTPUT` | `json` or `human` |
| `OMUMU_SITE` | Config profile name |
| `NO_COLOR` | Disable colored output |

## Authentication

**Browser login (recommended):**
```bash
omumu login --url https://yoursite.myomumu.com
```
Opens your browser for OAuth authorization. No API keys to manage.

**API key (for CI/automation):**
```bash
omumu login --url https://yoursite.myomumu.com --token <your-api-key>
```

Configuration is stored in `~/.omumu/config.json`.

## Commands

Run `omumu schema` for the full list with parameters. Here's the overview:

| Group | Commands |
|---|---|
| `course` | list, get, create, update, delete, duplicate |
| `module` | list, create, update |
| `lesson` | create, update |
| `lesson resource` | list, create, update, delete |
| `email sequence` | list, get, create, update |
| `email followup` | update |
| `quiz` | list, get, create, update |
| `question` | create, update |
| `answer` | create, update |
| `bucket` | create, update |
| `option point` | list, create, update, delete, batch |
| `optinform` | list, get, create, update, delete, add-field, remove-field, link-sequence |
| `page` | list, get, create, update, delete |
| `media` | generate image, set image |
| `debug` | throwables, outcome-log, sql, templates |

## How it works

The CLI talks JSON-RPC 2.0 to Omumu's MCP server. It's a thin client — no Omumu code or dependencies, just HTTP calls. The native binary is compiled with GraalVM, so there's no Java runtime required.

## Build from source

```bash
git clone https://github.com/omumuas/omumu-cli.git
cd omumu-cli

# JAR (requires Java 21+)
mvn clean package
java -jar target/omumu-cli-0.2.3.jar --help

# Native binary (requires GraalVM)
mvn clean package -Pnative
./target/omumu --help
```
