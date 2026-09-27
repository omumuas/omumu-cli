# Omumu CLI

Command-line interface for the [Omumu](https://omumu.com) customer education platform. Designed for humans and AI agents.

## Install

```bash
brew tap omumuas/omumu
brew install omumu
```

If Homebrew refuses with `Refusing to load formula ... from untrusted tap`, you have
`HOMEBREW_REQUIRE_TAP_TRUST` enabled (Homebrew 6+). Trust this tap once, then install:

```bash
brew trust omumuas/omumu
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

`omumu call` runs any Omumu tool your key can use. `omumu schema --json` lists them, with the
`omumu call` line for each (required arguments included):

```bash
omumu schema --json                                   # every tool, its parameters and its call line
omumu call omumu_offer_list                           # any tool, by name
omumu call offer_create --arg title="My offer" --arg totalAmountCents=4900 \
    --arg 'paymentProviders=["INVOICE_REQUEST"]'      # the omumu_ prefix is optional
omumu call omumu_page_update --input '{"slug":"home","contents":"# Hi"}'
```

Each `--arg key=value` is typed by the tool's schema: numbers, booleans, and JSON arrays or
objects are parsed, anything else is a string. `--input` takes all arguments as one JSON object,
and `--arg` values override its keys. An unknown argument or tool is refused with the known names.

A few common tasks also have their own commands:

| Command | Does |
|---|---|
| `omumu course list / get / create` | Courses |
| `omumu skill upload` | Upload a `.skill` bundle (platform admins) |
| `omumu status` | Check the connection |

### Uploading skills

A skill (workflow for AI assistants) is packaged as a `.skill` zip — a `SKILL.md`
plus any supporting files. Upload it with:

```bash
omumu skill upload path/to/create-course.skill           # creates as DRAFT
omumu skill upload path/to/create-course.skill --publish # publishes immediately
```

The CLI streams the bundle as `multipart/form-data` to `/mcp/skill/upload`, which
sidesteps the LLM-emitted-base64 limit that prevents Claude Desktop from invoking
`omumu_skill_upload` directly for non-trivial bundles. Requires platform admin.

## How it works

The CLI talks JSON-RPC 2.0 to Omumu's MCP server. It's a thin client — no Omumu code or dependencies, just HTTP calls. The native binary is compiled with GraalVM, so there's no Java runtime required.

## Build from source

```bash
git clone https://github.com/omumuas/omumu-cli.git
cd omumu-cli

# JAR (requires Java 21+)
mvn clean package
java -jar target/omumu-cli-0.4.0.jar --help

# Native binary (requires GraalVM)
mvn clean package -Pnative
./target/omumu --help
```
