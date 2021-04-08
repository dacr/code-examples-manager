enablePlugins(JavaAppPackaging)

Universal / mappings += {
    // we are using the reference.conf as default application.conf
    // the user can override settings here
    val conf = (resourceDirectory in Compile).value / "reference.conf"
    conf -> "conf/application.conf"
}

// add jvm parameter for typesafe config
bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../conf/application.conf""""

