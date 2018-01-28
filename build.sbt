lazy val pubsub = project
lazy val broker = project.dependsOn(pubsub)
lazy val subscriber = project.dependsOn(pubsub)