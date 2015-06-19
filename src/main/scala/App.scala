import java.io._
import java.net._

import akka.actor._
import akka.pattern
import akka.routing.RandomRouter
import akka.util.Timeout
import org.apache.commons.lang3.StringEscapeUtils

import scala.concurrent.duration._
import scala.io.Source

object App {

  case class Request(url: String)

  case class Response(response: String)

  case class Start()

  class Crawler extends Actor {
    def receive = {
      case Request(url) =>
        var content = ""
        try {
          val encoderUrl: String = "http://" + IDN.toASCII(url)
          val html: String = Source.fromURL(new URL(encoderUrl)).take(5000).mkString.trim
          val title: String = "<title>(.*?)<\\/title>".r.findFirstIn(html).mkString.stripPrefix("<title>").stripSuffix("</title>").replaceAll(",", "")
          val keywords: String = "keywords\"(.*?)>".r.findFirstIn(html).mkString.replaceAll("keywords\" content=\"", "").replaceAll("\\\"\\s?\\/?>", "").replaceAll(",", "")
          val description: String = "description\"(.*?)>".r.findFirstIn(html).mkString.replaceAll("description\" content=\"", "").replaceAll("\\\"\\s?\\/?>", "").replaceAll(",", "")
          content = decoder(title) + ", " + decoder(keywords) + ", " + decoder(description) + ", " + decoder(encoderUrl) + "\n"
        } catch {
          case e: UnknownHostException => content = ", , ,  UnknownHostException: " + url + "\n"
          case e: IOException => content = ", , ,  IOException: " + url + "\n"
          case unknown => content = ", , ,  Exception: " + url + "\n"
        }
        sender ! Response(content)
    }
  }

  class Supervisor extends Actor with ActorLogging {
    private var count = 0
    private var countDown = 0
    private val stringBuilder: StringBuilder = new StringBuilder
    private var requester: ActorRef = null
    private var fileWriter: FileWriter = null

    def receive = {
      case Start =>
        val randomRouter = context.actorOf(
          Props[Crawler].withRouter(RandomRouter(20)), "router")

        val stream: InputStream = getClass.getResourceAsStream("/short3.txt")
        for {
          line <- Source fromInputStream stream getLines()
        } {
          randomRouter ! Request(line)
          count += 1
        }
        stream.close()

        log.info("Count " + count)
        countDown = count
        requester = sender()
      case Response(response) =>
        try {
          if (fileWriter == null) {
            fileWriter = new FileWriter(new File("output.csv"), true)
          }

          countDown -= 1
          stringBuilder.append(response)

          if (countDown % 100 == 0) {
            fileWriter.append(stringBuilder.toString())
            fileWriter.flush()
            stringBuilder.clear()
            log.info("Count " + countDown)
          }

          if (countDown == 0) {
            fileWriter.close()
            log.info("Complete processed with " + count + " sites")
            context.stop(self)
            context.system.shutdown()
          }
        } catch {
          case e: IOException =>
            fileWriter.close()
            log.error("Fail processed with " + count + " sites")
            context.stop(self)
            context.system.shutdown()
        }
    }
  }


  def main(args: Array[String]) {

    val supervisor = ActorSystem().actorOf(Props(new Supervisor))
    implicit val timeout = Timeout(60 seconds)

    val result = pattern.ask(supervisor, Start)
  }

  object decoder extends (String => String) {
    def apply(x: String): String = StringEscapeUtils.unescapeXml(x)
  }

}