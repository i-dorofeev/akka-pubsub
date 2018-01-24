lazy val broker = project
lazy val subscriber = project.dependsOn(broker)