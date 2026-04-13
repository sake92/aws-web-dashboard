package app

import ba.sake.sharaf.{*, given}

object Views:

  def baseView(title: String, activeTab: String)(content: Html): Html = html"""
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>${title} — AWS UI</title>
      <link rel="stylesheet" href="/bulma.min.css">
      <link rel="stylesheet" href="/custom.css">
      <script src="/htmx.js"></script>
      <script src="/htmx-ext-sse.min.js"></script>
    </head>
    <body>
      <nav class="navbar is-light" role="navigation" aria-label="main navigation">
        <div class="navbar-brand">
          <a class="navbar-item has-text-weight-bold" href="/s3">
            ☁️ AWS UI
          </a>
          <a role="button" class="navbar-burger" aria-label="menu" aria-expanded="false"
             data-target="mainNavbar">
            <span aria-hidden="true"></span>
            <span aria-hidden="true"></span>
            <span aria-hidden="true"></span>
            <span aria-hidden="true"></span>
          </a>
        </div>
        <div id="mainNavbar" class="navbar-menu">
          <div class="navbar-end">
            <a class="navbar-item ${if activeTab == "s3" then "is-active has-text-weight-semibold" else ""}"
               href="/s3">S3</a>
            <a class="navbar-item ${if activeTab == "sqs" then "is-active has-text-weight-semibold" else ""}"
               href="/sqs">SQS</a>
          </div>
        </div>
      </nav>
      <section class="section">
        <div class="container">
          <div id="error-container"></div>
          <h1 class="title">${title}</h1>
          ${content}
        </div>
      </section>
    </body>
    </html>
  """

  def serviceUnavailable(message: String): Html = html"""
    <div class="notification is-warning">
      <p><strong>Could not connect to LocalStack.</strong></p>
      <p>Make sure Docker is running and LocalStack is available on port 4566.</p>
      <details class="mt-2">
        <summary class="is-size-7 has-text-grey" style="cursor:pointer">Error details</summary>
        <pre class="is-size-7 mt-1">${message}</pre>
      </details>
    </div>
  """

  def errorNotification(message: String): Html = html"""
    <div class="notification is-danger auto-dismiss">
      <button class="delete" onclick="this.parentElement.remove()"></button>
      ${message}
    </div>
  """
