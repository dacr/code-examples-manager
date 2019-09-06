# code-examples-manager
Manage code examples, provide gists/snippets publish mechanisms.
All my code examples are now shared using this tool, you can see
how it looks by taking a look at
[my public gists overview on github](https://gist.github.com/c071a7b7d3de633281cbe84a34be47f1). 

> Only github gists implementation is available.

## code examples 

In order to be published code examples must comes with a description header
inserted using line comments.

Example for languages using `//` for line comments :
```scala
// summary : Simplest scalatest test framework usage.
// keywords : scalatest, pi
// publish : gist, snippet
// authors : @crodav
// id : d24d8cb3-45c0-4d88-b033-7fae2325607b
import $ivy.`org.scalatest::scalatest:3.0.8`
import org.scalatest._,Matchers._
math.Pi shouldBe 3.14d +- 0.01d
```

request keys in description header are the following :
- **summary** : example one line summary.
- **keywords** : keywords describing your code features (comma separated).
- **publish** : publish destination keywords (`gist` for github).
- **authors** : code example authors list (comma separated)
- **id** : UUID for this code example. Generated using such commands :
  - [this ammonite scala script](https://gist.github.com/dacr/87c9636a6d25787d7c274b036d2a8aad).
  - This [ammonite](https://ammonite.io/) oneliner :  
    `amm -c 'println(java.util.UUID.randomUUID.toString)'`
  - This python oneliner :  
    `python -c "import uuid, sys;sys.stdout.write(str(uuid.uuid4()))"`

## configuration

|env or property name       | description
|---------------------------|----------------
|CEM_SEARCH_ROOTS           | examples search roots
|CEM_SEARCH_GLOB            | example file globs
|CEM_GITLAB_TOKEN           | gitlab authentication token for snippets API access
|CEM_GITHUB_TOKEN           | github authentication token for gists API access, see below for how to get this token
|CEM_EXAMPLES_OVERVIEW_UUID | the UUID for the overview GIST which list all examples, default value is `cafacafe-cafecafe`

Configuration examples :
```shell
export CEM_SEARCH_ROOTS="/tmp/someplace,/tmp/someotherplace"
export CEM_SEARCH_GLOB="*.{sc,sh}"
export CEM_GITHUB_TOKEN="fada-fada-fada-fada"
```

### Github authentication token configuration
Get an authorized access to github gist API :
- List authorizations : `curl --user "dacr" https://api.github.com/authorizations`
- **Create github authentication token with required authorization scopes** : 
  ```bash
  curl https://api.github.com/authorizations \
    --user "dacr" \
    --data '{"scopes":["gist", "read:user"],"note":"cem-oauth"}'
  ```
- Setup CEM_GITHUB_TOKEN environment variable with the previously generated token
  as shown within curl json response
- **Of course, keep it carefully as it is not possible to retrieve it later.**
