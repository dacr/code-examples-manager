# Code examples manager [![][CodeExamplesManagerImg]][CodeExamplesManagerLnk]

Manage code examples, provide gists/snippets publish mechanisms.
All my code examples (my programming knowledge base) are now shared using this tool,
you can see how it looks by taking a look at
[my public gists overview on github](https://gist.github.com/c071a7b7d3de633281cbe84a34be47f1). 

The origin of this tool comes from [this talk](https://www.youtube.com/watch?v=61AGIBdG7YE)
originally presented at [AlpesCraft 2019](https://www.alpescraft.fr/edition_2019/).

> Only github gists implementation is available.

## Why ?

Code examples are very important, each example is most of the time designed to focus
on a particular feature/characteristic of a programming language, a library or a framework.
They help us to quickly test, experiment and remember how bigger project or at least some
parts of them are working. 

Managing dozens of published code example files as gists (github) and/or snippets (gitlab)
is really not easy and time consuming, in particular if you want to keep your examples
up to date.

That's why I decided to automate their management. The first iteration was script based,
(and so shared as a gist) but with complexity increase, a dedicated project became 
necessary, and so **code-examples-manager** was born.

_Start small, make it works quickly, and then refactor !_

A lot of thanks to [Li Haoyi](https://github.com/lihaoyi) for his wonderful work on
[ammonite](http://ammonite.io/) which is probably the best solution for code examples and scripting
in [scala](https://www.scala-lang.org/).

## What it does

Code examples manager operations :
- It searches for code examples from the given directories roots
  - The search is achieved recursively
  - Only files with given extensions are selected
  - Code examples are taken into account if and only if they contain a unique identier (UUID)
    - See below for more details
- It publishes or updates remote code examples
  - Using code example publish scope (`publish` keyword)
    - `gist` is for github gists
  - It adds or updates a global summary of all examples

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
// execution : scala ammonite script (http://ammonite.io/) - run as follow 'amm scriptname.sc'
import $ivy.`org.scalatest::scalatest:3.0.8`
import org.scalatest._,Matchers._
math.Pi shouldBe 3.14d +- 0.01d
```

Request keys in description header are the following :
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
- **execution** : 

## configuration

|env or property name       | description
|---------------------------|----------------
|CEM_SEARCH_ROOTS           | examples search roots
|CEM_SEARCH_GLOB            | examples files globs
|CEM_GITLAB_TOKEN           | gitlab authentication token for snippets API access
|CEM_GITHUB_TOKEN           | github authentication token for gists API access, see below for how to get this token
|CEM_EXAMPLES_OVERVIEW_UUID | the UUID for the overview GIST which list all examples, default value is `cafacafe-cafecafe`

Configuration examples :
```shell
export CEM_SEARCH_ROOTS="/tmp/someplace,/tmp/someotherplace"
export CEM_SEARCH_GLOB="**/*.{sc,sh}"
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

[CodeExamplesManager]:    https://github.com/dacr/jaseries
[CodeExamplesManagerImg]: https://img.shields.io/maven-central/v/fr.janalyse/code-examples-manager_2.13.svg
[CodeExamplesManagerLnk]: https://search.maven.org/#search%7Cga%7C1%7Cfr.janalyse.code-examples-manager
