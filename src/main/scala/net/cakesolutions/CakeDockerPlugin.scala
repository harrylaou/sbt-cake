// Copyright: 2017 https://github.com/cakesolutions/sbt-cake/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0

package net.cakesolutions

import sbt._
import sbt.Keys._

/**
  * Cake recommended settings for configuring linter and wartremover, along
  * with a standard suite of compiler compatibility flags.
  */
object CakeDockerPlugin extends AutoPlugin {
  import com.typesafe.sbt.SbtNativePackager._
  import com.typesafe.sbt.packager.Keys._
  import com.typesafe.sbt.packager.docker._

  /**
    * When this plugin is enabled, {{autoImport}} defines a wildcard import for
    * set, eval, and .sbt files.
    */
  val autoImport = CakeDockerPluginKeys
  import autoImport._

  /** @see http://www.scala-sbt.org/0.13/api/index.html#sbt.package */
  override def requires: Plugins = CakeBuildInfoPlugin && DockerPlugin

  /** @see http://www.scala-sbt.org/0.13/api/index.html#sbt.package */
  override def trigger: PluginTrigger = noTrigger

  /** @see http://www.scala-sbt.org/0.13/api/index.html#sbt.package */
  override def projectSettings: Seq[Setting[_]] = Seq(
    dockerBaseImage := "openjdk:8-jre-alpine",
    dockerUpdateLatest := true,
    // we trigger dockerRepository on envvar because we do not want it
    // in publishLocal, but it gets picked up there, so we only pass
    // the envvar when doing the final publish.
    dockerRepository :=
      sys.env
        .get("DOCKER_REPOSITORY")
        .orElse(Some((name in ThisBuild).value)),
    packageName in Docker := name.value,
    maintainer in Docker := "Cake Solutions <devops@cakesolutions.net>",
    version in Docker := sys.props.get("tag").getOrElse(version.value),
    dockerPublishTags := Seq(),
    dockerBuildOptions ++= {
      val alias = dockerAlias.value
      dockerPublishTags.value
        .flatMap(tag => List("-t", alias.copy(tag = Some(tag)).versioned))
    },
    dockerCommands += {
      val dockerArgList =
        CakeBuildInfoKeys.generalInfo.value ++
          CakeBuildInfoKeys.dockerInfo.value
      val labelArguments =
        dockerArgList
          .map {
            case (key, value) =>
              s""""$key"="$value""""
          }

      Cmd("LABEL", labelArguments.mkString(" "))
    },
    CakeBuildInfoKeys.externalBuildTools ++= Seq(
      (
        "docker --version",
        "`docker` command should be installed and PATH accessible"
      )
    ),
    dockerRemove := dockerRemoveTask.value
  )

  private val dockerRemoveTask: Def.Initialize[Task[Unit]] = Def.task {
    val image = (name in Docker).value
    val repository = dockerRepository.value match {
      case None =>
        image
      case Some(repo) =>
        s"$repo/$image"
    }
    val lines = "docker images".!!.split("\\n").toList
    val Line = "^([^ ]+)[ ]+([^ ]+)[ ]+([^ ]+)[ ]+.*$".r
    val ids = lines.collect {
      case Line(repo, tag, id) if repo == repository =>
        id
    }
    // only need to delete the first one (they are aliases, subsequent
    // deletes fail)
    ids.headOption.foreach { id =>
      val res = s"docker rmi -f $id".!
      if (res != 0) {
        throw new IllegalStateException(s"`docker rmi -f $id` returned $res")
      }
    }
  }
}

/**
  * Cake docker settings
  */
object CakeDockerPluginKeys {

  /**
    * Task defining how docker images will be force removed
    */
  val dockerRemove: TaskKey[Unit] =
    taskKey[Unit](
      "Runs `docker rmi -f <ids>` for the images associated to the scope"
    )

  /**
    * Setting key defining extra tags that will be added to the
    * dockerBuildOptions
    */
  val dockerPublishTags: SettingKey[Seq[String]] =
    settingKey[Seq[String]](
      "Extra tags to publish other than version and latest"
    )
}
