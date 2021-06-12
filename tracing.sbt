Compile / resourceGenerators += Def.task {
  val dir            = (Compile / sourceManaged).value
  val projectName    = name.value
  val projectGroup   = organization.value
  val projectPage    = homepage.value.map(_.toString).getOrElse("https://github.com/dacr")
  val projectVersion = version.value
  val buildDateTime  = java.time.Instant.now().toString
  val buildUUID      = java.util.UUID.randomUUID.toString
  val file           = dir / "cem-meta.conf"
  IO.write(
    file,
    s"""code-examples-manager-config {
       |  meta-info {
       |    project-name = "$projectName"
       |    project-code = "cem"
       |    project-group = "$projectGroup"
       |    project-page = "$projectPage"
       |    build-version = "$projectVersion"
       |    build-date-time = "$buildDateTime"
       |    build-uuid = "$buildUUID"
       |  }
       |}""".stripMargin
  )
  Seq(file)
}.taskValue
