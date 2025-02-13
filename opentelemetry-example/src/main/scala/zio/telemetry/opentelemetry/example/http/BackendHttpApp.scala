package zio.telemetry.opentelemetry.example.http

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.{ TextMapGetter, TextMapPropagator }
import zio.telemetry.opentelemetry.Tracing
import zio.telemetry.opentelemetry.example.http.{ Status => ServiceStatus }
import zhttp.http.{ !!, ->, /, Headers, Http, HttpApp, Method, Response }
import zio.json.EncoderOps
import zio._

import java.lang
import scala.jdk.CollectionConverters._

case class BackendHttpApp(tracing: Tracing) {

  import tracing.aspects._

  val propagator: TextMapPropagator  = W3CTraceContextPropagator.getInstance()
  val getter: TextMapGetter[Headers] = new TextMapGetter[Headers] {
    override def keys(carrier: Headers): lang.Iterable[String] =
      carrier.headers.headersAsList.map(_._1).asJava

    override def get(carrier: Headers, key: String): String =
      carrier.headers.headerValue(key).orNull
  }

  val routes: HttpApp[Any, Throwable] =
    Http.collectZIO { case request @ Method.GET -> !! / "status" =>
      (for {
        _        <- tracing.addEvent("event from backend before response")
        response <- ZIO.succeed(Response.json(ServiceStatus.up("backend").toJson))
        _        <- tracing.addEvent("event from backend after response")
      } yield response) @@ spanFrom(propagator, request.headers, getter, "/status", SpanKind.SERVER)
    }

}

object BackendHttpApp {

  val live: URLayer[Tracing, BackendHttpApp] =
    ZLayer.fromFunction(BackendHttpApp.apply _)

}
