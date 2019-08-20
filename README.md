# code-examples-manager
Manage code examples, provide gists/snippets publish mechanisms.

## code examples 

## configuration

|env or property name       | description
|---------------------------|----------------
|CODE_EXAMPLES_SEARCH_ROOTS | examples search roots
|CODE_EXAMPLES_SEARCH_GLOB  | example file globs
|CODE_EXAMPLES_GITLAB_TOKEN | gitlab authentication token for snippets API access
|CODE_EXAMPLES_GITHUB_TOKEN | github authentication token for gists API access

Configuration examples :
```shell
export CODE_EXAMPLES_SEARCH_ROOTS="/tmp/someplace,/tmp/someotherplace"
export CODE_EXAMPLES_SEARCH_GLOB="*.{sc,sh}"
```
