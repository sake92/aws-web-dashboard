package app

import scala.jdk.CollectionConverters.*
import ba.sake.sharaf.{*, given}
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.core.sync.RequestBody

class S3Routes(s3: S3Client):
  val routes = Routes:
    // --- Buckets ---
    case GET -> Path("s3") =>
      val buckets = s3.listBuckets().buckets().asScala.toList
      Response.withBody(Views.baseView("S3 Buckets", "s3")(html"""
        <form hx-post="/s3/buckets"
              hx-target="#bucket-table-body"
              hx-swap="beforeend"
              hx-on::after-request="if(event.detail.successful) this.reset()"
              class="mb-5">
          <div class="field has-addons">
            <div class="control is-expanded">
              <input class="input" name="name" placeholder="my-new-bucket" required>
            </div>
            <div class="control">
              <button class="button is-primary" type="submit">Create Bucket</button>
            </div>
          </div>
        </form>

        <div class="table-container">
          <table class="table is-fullwidth is-hoverable is-striped">
            <thead>
              <tr>
                <th>Name</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody id="bucket-table-body">
              ${if buckets.isEmpty then html"""
                <tr id="empty-row">
                  <td colspan="3" class="has-text-centered has-text-grey-light">
                    No buckets yet — create one above
                  </td>
                </tr>
              """ else html"${buckets.map(b => bucketRow(b.name(), b.creationDate().toString))}"}
            </tbody>
          </table>
        </div>
      """))

    case POST -> Path("s3", "buckets") =>
      try
        val form = Request.current.bodyForm[(name: String)]
        val newBucketName = form.name.toLowerCase().trim()
        s3.createBucket(CreateBucketRequest.builder().bucket(newBucketName).build())
        val bucket = s3.listBuckets().buckets().asScala.find(_.name() == newBucketName)
        val created = bucket.map(_.creationDate().toString).getOrElse("")
        Response.withBody(bucketRow(newBucketName, created))
      catch case e: S3Exception =>
        Response.withBody(Views.errorNotification(e.getMessage))
          .settingHeader("HX-Retarget", "#error-container")
          .settingHeader("HX-Reswap", "afterbegin")

    case DELETE -> Path("s3", "buckets", bucketName) =>
      try
        s3.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build())
        Response.withBody(html"")
      catch case e: S3Exception =>
        Response.withBody(Views.errorNotification(e.getMessage))
          .settingHeader("HX-Retarget", "#error-container")
          .settingHeader("HX-Reswap", "afterbegin")

    // --- Objects ---
    case GET -> Path("s3", "buckets", bucketName, "objects") =>
      val objects = s3
        .listObjects(ListObjectsRequest.builder().bucket(bucketName).build())
        .contents().asScala.toList
      Response.withBody(Views.baseView(s"Bucket: $bucketName", "s3")(html"""
        <nav class="breadcrumb" aria-label="breadcrumbs">
          <ul>
            <li><a href="/s3">S3 Buckets</a></li>
            <li class="is-active"><a href="#">${bucketName}</a></li>
          </ul>
        </nav>

        <form hx-post="/s3/buckets/${bucketName}/objects"
              hx-target="#object-table-body"
              hx-swap="beforeend"
              hx-encoding="multipart/form-data"
              hx-on::after-request="if(event.detail.successful) this.reset()"
              class="mb-5">
          <div class="field is-grouped">
            <div class="control">
              <div class="file has-name">
                <label class="file-label">
                  <input class="file-input" type="file" name="content" required
                         onchange="this.closest('.file').querySelector('.file-name').textContent = this.files[0]?.name || 'No file selected'">
                  <span class="file-cta">
                    <span class="file-label">Choose file…</span>
                  </span>
                  <span class="file-name">No file selected</span>
                </label>
              </div>
            </div>
            <div class="control is-expanded">
              <input class="input" name="key" placeholder="object-key" required>
            </div>
            <div class="control">
              <button class="button is-primary" type="submit">Upload</button>
            </div>
          </div>
        </form>

        <div class="table-container">
          <table class="table is-fullwidth is-hoverable is-striped">
            <thead>
              <tr>
                <th>Key</th>
                <th>Size</th>
                <th>Last Modified</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody id="object-table-body">
              ${if objects.isEmpty then html"""
                <tr id="empty-row">
                  <td colspan="4" class="has-text-centered has-text-grey-light">
                    No objects in this bucket
                  </td>
                </tr>
              """ else html"${objects.map(o => objectRow(bucketName, o.key(), formatSize(o.size()), o.lastModified().toString))}"}
            </tbody>
          </table>
        </div>
      """))

    case POST -> Path("s3", "buckets", bucketName, "objects") =>
      try
        val form = Request.current.bodyForm[(key: String, content: java.nio.file.Path)]
        s3.putObject(
          PutObjectRequest.builder().bucket(bucketName).key(form.key).build(),
          RequestBody.fromFile(form.content)
        )
        val obj = s3.headObject(HeadObjectRequest.builder().bucket(bucketName).key(form.key).build())
        Response.withBody(objectRow(bucketName, form.key, formatSize(obj.contentLength()), obj.lastModified().toString))
      catch case e: S3Exception =>
        Response.withBody(Views.errorNotification(e.getMessage))
          .settingHeader("HX-Retarget", "#error-container")
          .settingHeader("HX-Reswap", "afterbegin")

    case GET -> Path("s3", "buckets", bucketName, "objects", key, "download") =>
      try
        val obj = s3.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build())
        val inputStream: java.io.InputStream = obj
        Response.withBody(inputStream)
          .settingHeader("Content-Disposition", s"""attachment; filename="$key"""")
      catch case e: S3Exception =>
        Response.redirect(s"/s3/buckets/$bucketName/objects")

    case DELETE -> Path("s3", "buckets", bucketName, "objects", key) =>
      try
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build())
        Response.withBody(html"")
      catch case e: S3Exception =>
        Response.withBody(Views.errorNotification(e.getMessage))
          .settingHeader("HX-Retarget", "#error-container")
          .settingHeader("HX-Reswap", "afterbegin")

  private def bucketRow(name: String, created: String): Html = html"""
    <tr>
      <td>${name}</td>
      <td>${created}</td>
      <td>
        <div class="buttons are-small">
          <a class="button is-link is-light" href="/s3/buckets/${name}/objects">
            View Objects
          </a>
          <button class="button is-danger is-light"
                  hx-delete="/s3/buckets/${name}"
                  hx-target="closest tr"
                  hx-swap="outerHTML"
                  hx-confirm="Delete bucket ${name}?">
            Delete
          </button>
        </div>
      </td>
    </tr>
  """

  private def objectRow(bucketName: String, key: String, size: String, lastModified: String): Html = html"""
    <tr>
      <td>${key}</td>
      <td>${size}</td>
      <td>${lastModified}</td>
      <td>
        <div class="buttons are-small">
          <a class="button is-link is-light"
             href="/s3/buckets/${bucketName}/objects/${key}/download">
            Download
          </a>
          <button class="button is-danger is-light"
                  hx-delete="/s3/buckets/${bucketName}/objects/${key}"
                  hx-target="closest tr"
                  hx-swap="outerHTML"
                  hx-confirm="Delete object ${key}?">
            Delete
          </button>
        </div>
      </td>
    </tr>
  """

  private def formatSize(bytes: Long): String =
    if bytes < 1024 then s"$bytes B"
    else if bytes < 1024 * 1024 then f"${bytes / 1024.0}%.1f KB"
    else if bytes < 1024 * 1024 * 1024 then f"${bytes / (1024.0 * 1024)}%.1f MB"
    else f"${bytes / (1024.0 * 1024 * 1024)}%.1f GB"
