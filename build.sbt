name := """studybuddy"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava, PlayEbean)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  jdbc,
  javaJdbc,
  "org.mindrot" % "jbcrypt" % "0.3m",
  "org.postgresql" % "postgresql" % "9.4-1206-jdbc42",
  "org.json" % "json" % "20090211",
  "org.apache.httpcomponents" % "httpmime" % "4.5.1",
  "org.imgscalr" % "imgscalr-lib" % "4.2",
  "com.sendgrid" % "sendgrid-java" % "3.1.0",
  cache,
  javaWs
)

dependencyOverrides += "org.apache.httpcomponents" % "httpclient" % "4.5.1"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator