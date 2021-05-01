//enablePlugins(CodegenPlugin)

/* ---------------------------------------------------------------------------------------------------------------------
 Generated code can be quite huge and exceed intellij default limits, so add this custom property :
 `idea.max.intellisense.filesize=3500`
 */

/* ---------------------------------------------------------------------------------------------------------------------
  SCHEMA download :
  - curl -o project/github-schema.graphql https://docs.github.com/public/schema.docs.graphql
  - curl -o project/gitlab-schema.graphql https://github.com/gitlabhq/gitlabhq/blob/master/doc/api/graphql/reference/gitlab_schema.graphql
 */

/* ---------------------------------------------------------------------------------------------------------------------
// Command to execute in order to generate the boiler plate code...
  - mkdir -p target/scala-2.13/src_managed/main/fr/janalyse/cem/graphql/github/
  - mkdir -p target/scala-2.13/src_managed/main/fr/janalyse/cem/graphql/gitlab/
  - calibanGenClient project/github-schema.graphql target/scala-2.13/src_managed/main/fr/janalyse/cem/graphql/github/Client.scala --genView false --packageName fr.janalyse.cem.graphql.github
  - calibanGenClient project/gitlab-schema.graphql target/scala-2.13/src_managed/main/fr/janalyse/cem/graphql/gitlab/Client.scala --genView false --packageName fr.janalyse.cem.graphql.gitlab

(calibanGenClient project/github-schema.graphql src/main/scala/fr/janalyse/cem/graphql/github/GitHubClient.scala --genView false)

*/

//Compile / sourceGenerators += Def.task {
//  val inputFile = file("project/github-schema.graphql")
//  val outputFile = (Compile / sourceManaged).value / "fr" / "janalyse" / "cem" / "graphql" / "github" / "Client.scala"
//  outputFile.getParentFile.mkdirs()
//  if (!outputFile.exists()) outputFile.createNewFile()
//  // TODO when possible
//  println(outputFile.file.toString)
//  Seq(outputFile)
//}.taskValue
