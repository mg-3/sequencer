package com.arrow.digital.service.ong.verticles

import io.vertx.core.AbstractVerticle
import io.vertx.core.Context
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Order Number Generator Service
 *
 * @author mgarcia
 */
open class ONGService : AbstractVerticle() {

    private val logger: Logger = LoggerFactory.getLogger(ONGService::class.java)
    private val permissible_len = 7
    private val formatStr = "%0"+ permissible_len.toString() + "d"
    private val prefix = "WEB-SOA"
    private val byteBuffer = ByteBuffer.allocateDirect(permissible_len)

    private var sequencer = AtomicInteger(0)
    private lateinit var asyncFileChannel: AsynchronousFileChannel

    override fun init(vertx: Vertx?, context: Context?) {
        super.init(vertx, context)
        asyncFileChannel = asyncFileChannel()
        logger.info("Initializing verticle... $context")
        byteBuffer.clear()
        byteBuffer.clear()
        val future = asyncFileChannel.read(byteBuffer, 0L)
        val read = future.get()
        if (read > 0) {
            byteBuffer.flip()
            val bytes = ByteArray(permissible_len)
            byteBuffer.get(bytes)
            sequencer = AtomicInteger(String(bytes).toInt())
            logger.info("Read previously stored WEB-SOA from file, initializing - ${String(bytes)}")
        }
    }

    override fun start() {
        val server = vertx.createHttpServer()
        val router = Router.router(vertx)

        router.route(HttpMethod.GET, "/ong/next")
                .handler(
                        { routingContext ->
                            val response = routingContext.response()
                            response.isChunked = true
                            response.putHeader("content-type", "application/json")
                            val next = nextOrderNumber()
                            val json = JsonObject().put("order-number", JsonObject().put("next", next))
                            response.write(json.encodePrettily())
                            dumpLatestOrderNumber(next)
                            routingContext.response().end()
                            logger.info("Last issued order number was: $next")
                        }
                ).enable()

        router.route(HttpMethod.GET, "/ong/last")
                .handler(
                        { routingContext ->
                            val response = routingContext.response()
                            response.isChunked = true
                            response.putHeader("content-type", "application/json")
                            val last = lastIssuedOrderNumber()
                            val json = JsonObject().put("order-number", JsonObject().put("last", last))
                            response.write(json.encodePrettily())
                            routingContext.response().end()
                        }
                ).enable()

        val hostname = context.config().getString("hostname")
        val port = context.config().getInteger("port")

        server.requestHandler({ router.accept(it) })
                .listen(port, hostname, { res ->
                    if (res.succeeded()) {
                        logger.info("Server is now listening!")
                    } else {
                        logger.error("Failed to start!")
                    }
                })
    }

    override fun stop() {
        this.asyncFileChannel.close()
        logger.debug("Output file closed.")
    }

    private fun nextOrderNumber() : String {
        val id = formatStr.format(sequencer.incrementAndGet())
        if (id.length > permissible_len) {
            throw RuntimeException("We have passed the legal id limit!! ALERT CODE RED-1")
        }
        return prefix + id
    }

    private fun lastIssuedOrderNumber() : String {
        return prefix + formatStr.format(sequencer.get())
    }

    private fun dumpLatestOrderNumber(webso: String) {
        val oid = webso.substring(prefix.length)
        byteBuffer.clear()
        byteBuffer.put(oid.toByteArray()).flip()
        asyncFileChannel.write(byteBuffer, 0L, byteBuffer, object : CompletionHandler<Int, ByteBuffer> {
            override fun completed(result: Int?, attachment: ByteBuffer?) {
                logger.debug("Bytes written: $result")
            }

            override fun failed(exc: Throwable?, attachment: ByteBuffer?) {
                logger.error("Could not write order number to file", exc)
            }
        })
    }

    private fun asyncFileChannel() : AsynchronousFileChannel {
        val trackingFile = File(".latest_ordnum")
        val asyncChannel = AsynchronousFileChannel.open(Paths.get(trackingFile.toURI()),
                StandardOpenOption.CREATE,
                StandardOpenOption.DSYNC,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)
        asyncChannel.force(true)
        return asyncChannel
    }

}