# CEM - Code Examples Manager [![][CodeExamplesManagerImg]][CodeExamplesManagerLnk] ![Scala CI][scalaci-master]

Code example manager (CEM) is a software tool which manage your code examples
and provide publish mechanisms to [github.com][githubcom] ([gists][gists]) or
[gitlab.com][gitlabcom] ([snippets][snippets]).

Current [Code example manager (CEM)][cem] implementation is just a command line tool
which compare locally available examples with already published ones in order to find
what it should do (add, update, do nothing). 

All my code examples (my programming knowledge base) are now shared using this tool,
you can take a look to [my public gists overview on github][mygists] to illustrate the 
publishing work achieved by CEM. 

The origin of this tool comes from [this talk][ac2019talk] originally presented at [AlpesCraft 2019][ac2019].

## Why ?

Code examples are very important, each example is most of the time designed to focus
on a particular feature/characteristic of a programming language, a library or a framework.
They help us to quickly test, experiment and remember how bigger project or at least some
parts of them are working. 

Managing hundreds of published code example files as gists (github) and/or snippets (gitlab)
is really not easy and time-consuming, in particular if you want to keep them up to date.

That's why I've decided to automate their management. The first iteration was script based,
(and so shared as a gist) but with complexity increase, a dedicated project became 
necessary, and so **code-examples-manager** was born. After a huge refactoring, which has
introduced mustache templating, better configuration management, multiple publishing targets,
gitlab snippets support, **code-examples-manager** is now mature.

_Start small, make it works quickly, and then refactor !_

A lot of thanks to [Li Haoyi][lihaoyi] for his wonderful work on [ammonite][amm] which is
probably the best solution for code examples and scripting in [scala][scala].

## Quick start

No particular prerequisites, just a Java >=8 JVM available, and
it will run on your Linux, Windows or MacOSX

Instructions example with github.com publishing configuration :
- Download latest archive `code-examples-manager-*.tgz` from
  + https://github.com/dacr/code-examples-manager/releases/latest
- Install somewhere
  + `tar xvfz code-examples-manager-*.tgz`
  + Add the bin directory to your PATH
- Customize your configuration (see below for token configuration)
  ```
  export CEM_SEARCH_ROOTS="/home/myuser/myexamples"
  export CEM_SEARCH_GLOB="**/*.{sc,sh,*.md,*.jsh}"
  export CEM_GITHUB_TOKEN="xxxxx"
  ```
- Create an example file in `/home/myuser/myexamples` such as `hello.md`
  ```
  <!--
  // summary : my markdown cheat sheet
  // keywords : markdown, hello, example 
  // publish : gist
  // authors : someone, someonelse
  // id : d27245fc-22fb-4c9e-9809-feb0826400e7
  -->
  # Hello world !
  this is just an example
  ```
- Run the following command from your terminal :
  ```
  code-examples-manager
  ```
- Check the command output to get the overview URL


## Code examples

In order to be published your code examples must come with a description header
inserted using single line comments. You must provide a unique identifier (UUID)
to each of your example, as well as a summary and publish keywords which define
remote destinations.

Example for languages using `//` for line comments :
```scala
// summary : Simplest scalatest test framework usage.
// keywords : scala, scalatest, pi, @testable
// publish : gist, snippet
// authors : David Crosson
// license : Apache
// id : d24d8cb3-45c0-4d88-b033-7fae2325607b
// execution : scala ammonite script (http://ammonite.io/) - run as follow 'amm scriptname.sc'
import $ivy.`org.scalatest::scalatest:3.2.0`
import org.scalatest._,matchers.should.Matchers._
math.Pi shouldBe 3.14d +- 0.01d

```

Request keys in description header are the following :
- **`summary`** : example one line summary.
- **`keywords`** : keywords describing your code features (comma separated).
- **`publish`** : publish destination keywords (comma separated)
  - the default configuration file provide those activation keywords :
    - `gist` : for github.com
    - `snippet` : for gitlab.com
- **`authors`** : code example authors list (comma separated).
- **`license`** : the example license.
- **`id`** : UUID for this code example. Generated using such commands :
  - [this ammonite scala script][uuid-sc].
  - This [ammonite][amm] oneliner :  
    `amm -c 'println(java.util.UUID.randomUUID.toString)'`
  - This python oneliner :  
    `python -c "import uuid, sys;sys.stdout.write(str(uuid.uuid4()))"`
  - This linux command (comes from package named uuid-runtime at least on debian based linux) :  
    `uuidgen`
- **execution** : how to execute the example, execution runtime release constraints, ...

## CEM operations

Code examples manager operations :
- It reads its configuration
- It searches for code examples from the given directories roots
  - Only files with given extensions are selected (the given glob)
  - Selects code examples if and only if they contain a unique identifier (UUID)
- It publishes or updates remote code examples to remote destinations
  - the code example publish scope (`publish` keyword) select target destinations
    - comma separated publish activation keyword (`activation-keyword` parameter in configuration) 
  - It adds or updates a global overview of all published examples for a given destination
    - this summary has its own UUID defined in the configuration file 

## Configuration

The configuration relies on configuration files, a default one named `reference.conf` is provided.
This [default configuration file][referenceconf] defines default values and default behaviors and
allow a simple configuration way based on environment variables which override default values.

### Simplified configuration

|env or property name       | description
|---------------------------|----------------
|CEM_SEARCH_ROOTS           | examples search roots
|CEM_SEARCH_GLOB            | examples files globs
|CEM_GITLAB_TOKEN           | gitlab authentication token for snippets API access
|CEM_GITHUB_TOKEN           | github authentication token for gists API access, see below for how to get this token
|CEM_EXAMPLES_OVERVIEW_UUID | the fixed UUID for the overview GIST which list all examples, default value is `cafacafe-cafecafe`

Configuration examples :
```shell
export CEM_SEARCH_ROOTS="/tmp/someplace,/tmp/someotherplace"
export CEM_SEARCH_GLOB="**/*.{sc,sh,*.md,*.jsh}"
export CEM_GITHUB_TOKEN="fada-fada-fada-fada"
```

### Advanced configuration

Take a look to the [configuration file][referenceconf] for more information about advanced configuration.

Once CEM installed you can modify the provided `conf/application.conf` file (whose content is by default
the same as the default [reference.conf][referenceconf] file), remember that any unset parameter in `application.conf`
will default to the value defined in `reference.conf`.

Note : It is also possible to provide a custom configuration file through the `config.file` java property.

## Authentication tokens

### Gitlab authentication token configuration

Get an access token from gitlab :
- Go to your user **settings**
  - Select **Access tokens**
    - Add a **Personal access token**
      - Enable scopes : `api` and `read_user`
- setup your `CEM_GITLAB_TOKEN` environment variable or `token` parameter in your configuration file
  with the generated token
- **Keep it carefully as it is not possible to retrieve it later.**
- **And of course KEEP IT SECURE**

### Github authentication token configuration

Get an access token from gitlab.com :
- Got to your user **settings**
  - Select **Developer settings**
    - Select **Personal access tokens**
      - Then **generate new token**
        - Enable scopes : `gist` and `read:user`
- setup your `CEM_GITHUB_TOKEN` environment variable or `token` parameter in your configuration file
  with the generated token, the value shown within curl json response
- **Keep it carefully as it is not possible to retrieve it later.**
- **And of course KEEP IT SECURE**



[CodeExamplesManager]:    https://github.com/dacr/jaseries
[CodeExamplesManagerImg]: https://img.shields.io/maven-central/v/fr.janalyse/code-examples-manager_2.13.svg
[CodeExamplesManagerLnk]: https://search.maven.org/#search%7Cga%7C1%7Cfr.janalyse.code-examples-manager
[scalaci-master]: https://github.com/dacr/code-examples-manager/workflows/Scala%20CI/badge.svg
[mygists]: https://gist.github.com/c071a7b7d3de633281cbe84a34be47f1
[cem]: https://github.com/dacr/code-examples-manager
[amm]: https://ammonite.io/
[githubcom]: https://github.com/
[gitlabcom]: https://gitlab.com/
[snippets]: https://docs.gitlab.com/ce/user/snippets.html
[gists]: https://docs.github.com/en/github/writing-on-github/creating-gists
[uuid-sc]: https://gist.github.com/dacr/87c9636a6d25787d7c274b036d2a8aad
[scala]: https://www.scala-lang.org/
[lihaoyi]: https://github.com/lihaoyi
[ac2019]: https://www.alpescraft.fr/edition_2019/
[ac2019talk]: https://www.youtube.com/watch?v=61AGIBdG7YE
[referenceconf]: https://github.com/dacr/code-examples-manager/blob/master/src/main/resources/reference.conf
[latest]: https://github.com/dacr/code-examples-manager/releases/latest