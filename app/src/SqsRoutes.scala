package app

import scala.jdk.CollectionConverters.*
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.*
import ba.sake.sharaf.{*, given}

class SqsRoutes(sqs: SqsClient):
  val routes = Routes:
    // --- Queues ---
    case GET -> Path("sqs") =>
      try
        val queues = sqs.listQueues().queueUrls().asScala.toList
        Response.withBody(Views.baseView("SQS Queues", "sqs")(html"""
          <form hx-post="/sqs/queues"
                hx-target="#queue-table-body"
                hx-swap="beforeend"
                hx-on::after-request="if(event.detail.successful) this.reset()"
                class="mb-5">
            <div class="field has-addons">
              <div class="control is-expanded">
                <input class="input" name="name" placeholder="my-new-queue" required>
              </div>
              <div class="control">
                <button class="button is-primary" type="submit">Create Queue</button>
              </div>
            </div>
          </form>

          <div class="table-container">
            <table class="table is-fullwidth is-hoverable is-striped">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>URL</th>
                  <th>Messages (approx)</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody id="queue-table-body">
                ${if queues.isEmpty then html"""
                  <tr id="empty-row">
                    <td colspan="4" class="has-text-centered has-text-grey-light">
                      No queues yet — create one above
                    </td>
                  </tr>
                """ else html"${queues.map(url => queueRow(queueNameFromUrl(url), url, getApproxMessageCount(url)))}"}
              </tbody>
            </table>
          </div>
        """))
      catch
        case e: Exception =>
          Response.withBody(Views.baseView("SQS Queues", "sqs")(Views.serviceUnavailable(e.getMessage)))

    case POST -> Path("sqs", "queues") =>
      try
        val form = Request.current.bodyForm[(name: String)]
        val queueName = form.name.trim()
        val result = sqs.createQueue(CreateQueueRequest.builder().queueName(queueName).build())
        val count = getApproxMessageCount(result.queueUrl())
        Response.withBody(queueRow(queueName, result.queueUrl(), count))
      catch
        case e: SqsException =>
          Response
            .withBody(Views.errorNotification(e.getMessage))
            .settingHeader("HX-Retarget", "#error-container")
            .settingHeader("HX-Reswap", "afterbegin")

    case DELETE -> Path("sqs", "queues", queueName) =>
      try
        val queueUrl = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl()
        sqs.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl).build())
        Response.withBody(html"")
      catch
        case e: SqsException =>
          Response
            .withBody(Views.errorNotification(e.getMessage))
            .settingHeader("HX-Retarget", "#error-container")
            .settingHeader("HX-Reswap", "afterbegin")

    // --- Messages ---
    case GET -> Path("sqs", "queues", queueName, "messages") =>
      Response.withBody(Views.baseView(s"Queue: $queueName", "sqs")(html"""
        <nav class="breadcrumb" aria-label="breadcrumbs">
          <ul>
            <li><a href="/sqs">SQS Queues</a></li>
            <li class="is-active"><a href="#">${queueName}</a></li>
          </ul>
        </nav>

        <div class="level">
          <div class="level-left"></div>
          <div class="level-right">
            <button class="button is-warning is-light"
                    hx-post="/sqs/queues/${queueName}/purge"
                    hx-target="#purge-result"
                    hx-swap="innerHTML"
                    hx-confirm="Purge all messages from ${queueName}?">
              Purge Queue
            </button>
          </div>
        </div>
        <div id="purge-result" class="mb-4"></div>

        <div class="columns">
          <div class="column is-half">
            <div class="box">
              <h2 class="subtitle">Send Message</h2>
              <form hx-post="/sqs/queues/${queueName}/messages"
                    hx-target="#send-result"
                    hx-swap="innerHTML"
                    hx-on::after-request="if(event.detail.successful) this.reset()">
                <div class="field">
                  <div class="control">
                    <textarea class="textarea" name="body" placeholder="Message body..." rows="4" required></textarea>
                  </div>
                </div>
                <div class="field">
                  <div class="control">
                    <button class="button is-primary" type="submit">Send</button>
                  </div>
                </div>
              </form>
              <div id="send-result" class="mt-3"></div>
            </div>
          </div>

          <div class="column is-half">
            <div class="box">
              <h2 class="subtitle">Receive Messages</h2>
              <div id="receive-container">
                <button class="button is-info"
                        hx-get="/sqs/queues/${queueName}/messages/receive-start"
                        hx-target="#receive-container"
                        hx-swap="innerHTML">
                  Start Receiving
                </button>
              </div>
            </div>
          </div>
        </div>
      """))

    case POST -> Path("sqs", "queues", queueName, "messages") =>
      try
        val form = Request.current.bodyForm[(body: String)]
        val queueUrl = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl()
        val result = sqs.sendMessage(
          SendMessageRequest
            .builder()
            .queueUrl(queueUrl)
            .messageBody(form.body)
            .build()
        )
        Response.withBody(html"""
          <div class="notification is-success is-light">
            Message sent — ID: <code>${result.messageId()}</code>
          </div>
        """)
      catch
        case e: SqsException =>
          Response
            .withBody(Views.errorNotification(e.getMessage))
            .settingHeader("HX-Retarget", "#error-container")
            .settingHeader("HX-Reswap", "afterbegin")

    case POST -> Path("sqs", "queues", queueName, "purge") =>
      try
        val queueUrl = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl()
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build())
        Response.withBody(html"""
          <div class="notification is-success is-light">
            Queue purged successfully.
          </div>
        """)
      catch
        case e: SqsException =>
          Response
            .withBody(Views.errorNotification(e.getMessage))
            .settingHeader("HX-Retarget", "#error-container")
            .settingHeader("HX-Reswap", "afterbegin")

    case GET -> Path("sqs", "queues", queueName, "messages", "receive-start") =>
      try
        val queueUrl = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl()
        Response.withBody(html"""
          <div class="sse-status mb-3">
            <span class="sse-dot is-active"></span>
            <span>Listening… (60s)</span>
          </div>
          <div hx-ext="sse"
               sse-connect="/sqs/queues/${queueName}/messages/receive"
               sse-close="stop">
            <div id="received-messages" sse-swap="message" hx-swap="afterbegin">
            </div>
          </div>
          <script>
            setTimeout(function() {
              var dot = document.querySelector('.sse-dot');
              var text = dot && dot.nextElementSibling;
              if (dot) { dot.classList.remove('is-active'); }
              if (text) { text.textContent = 'Stopped — 60s elapsed'; }
            }, 62000);
          </script>
        """)
      catch
        case e: SqsException =>
          Response
            .withBody(Views.errorNotification(e.getMessage))
            .settingHeader("HX-Retarget", "#error-container")
            .settingHeader("HX-Reswap", "afterbegin")

    case GET -> Path("sqs", "queues", queueName, "messages", "receive") =>
      val queueUrl = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl()
      val sseSender = SseSender()
      Thread.startVirtualThread: () =>
        val startTime = System.currentTimeMillis()
        val timeoutMs = 60_000L
        try
          while System.currentTimeMillis() - startTime < timeoutMs do {
            val remainingMs = timeoutMs - (System.currentTimeMillis() - startTime)
            val waitSeconds = Math.min(20, Math.max(1, remainingMs / 1000)).toInt
            val response = sqs.receiveMessage(
              ReceiveMessageRequest
                .builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(waitSeconds)
                .build()
            )
            for msg <- response.messages().asScala do
              val timestamp = java.time.Instant.now().toString
              sseSender.send(
                ServerSentEvent.Message(
                  data = html"""
                  <div class="box mb-3">
                    <p class="mb-2">${msg.body()}</p>
                    <p class="is-size-7 has-text-grey">
                      ID: <code>${msg.messageId()}</code>
                      &nbsp;·&nbsp; Received: ${timestamp}
                    </p>
                  </div>
                """.toString
                )
              )
              sqs.deleteMessage(
                DeleteMessageRequest
                  .builder()
                  .queueUrl(queueUrl)
                  .receiptHandle(msg.receiptHandle())
                  .build()
              )
          }
          sseSender.send(ServerSentEvent.Done())
        catch
          case _: Exception =>
            sseSender.send(ServerSentEvent.Done())
      Response.withBody(sseSender)

  private def queueNameFromUrl(url: String): String =
    url.split("/").last

  private def getApproxMessageCount(queueUrl: String): String =
    try
      val attrs = sqs
        .getQueueAttributes(
          GetQueueAttributesRequest
            .builder()
            .queueUrl(queueUrl)
            .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
            .build()
        )
        .attributes()
        .asScala
      attrs.getOrElse(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0")
    catch case _: Exception => "?"

  private def queueRow(name: String, url: String, messageCount: String): Html = html"""
    <tr>
      <td>${name}</td>
      <td><span class="is-size-7 has-text-grey">${url}</span></td>
      <td>${messageCount}</td>
      <td>
        <div class="buttons are-small">
          <a class="button is-link is-light" href="/sqs/queues/${name}/messages">
            Messages
          </a>
          <button class="button is-danger is-light"
                  hx-delete="/sqs/queues/${name}"
                  hx-target="closest tr"
                  hx-swap="outerHTML"
                  hx-confirm="Delete queue ${name}?">
            Delete
          </button>
        </div>
      </td>
    </tr>
  """
