package app

import java.net.URI
import ba.sake.sharaf.{*, given}
import ba.sake.sharaf.undertow.UndertowSharafServer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.auth.credentials.*

object Main:

  val credProvider = new AwsCredentialsProvider:
    override def resolveCredentials(): AwsCredentials =
      AwsBasicCredentials.create("test", "test")

  val localStackEndpoint = URI.create("http://localhost:4566")

  val s3 = S3Client
    .builder()
    .endpointOverride(localStackEndpoint)
    .credentialsProvider(credProvider)
    .region(Region.US_EAST_1)
    .forcePathStyle(true)
    .build()

  val sqs = SqsClient
    .builder()
    .endpointOverride(localStackEndpoint)
    .credentialsProvider(credProvider)
    .region(Region.US_EAST_1)
    .build()

  def main(args: Array[String]): Unit =
    val mainRoutes = Routes:
      case GET -> Path() =>
        Response.redirect("/s3")

    val routes = Routes.merge(Seq(
      mainRoutes,
      S3Routes(s3).routes,
      SqsRoutes(sqs).routes
    ))

    UndertowSharafServer("localhost", 8181, routes).start()
    println(s"Server started at http://localhost:8181")
