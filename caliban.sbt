enablePlugins(CodegenPlugin)

/*
 Generated code can be quite huge and exceed intellij default limits, so add this custom property :
 `idea.max.intellisense.filesize=3500`
 */

/*
// Command to execute in order to generate the boiler plate code...

calibanGenClient project/github-schema.graphql src/main/scala/fr/janalyse/cem/graphql/github/GitHubClient.scala --genView false
calibanGenClient project/github-schema.graphql target/scala-2.13/src_managed/main/fr/janalyse/cem/graphql/github/Client.scala --genView false --packageName fr.janalyse.cem.graphql.github

*/

Compile / sourceGenerators += Def.task {
  val inputFile = file("project/github-schema.graphql")
  val outputFile = (Compile / sourceManaged).value / "fr" / "janalyse" / "cem" / "graphql" / "github" / "Client.scala"
  outputFile.getParentFile.mkdirs()
  if (!outputFile.exists()) outputFile.createNewFile()
  // TODO when possible
  println(outputFile.file.toString)
  Seq(outputFile)
}.taskValue
