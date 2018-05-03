package com.arrow.digital.service.ong

import com.arrow.digital.service.ong.verticles.ONGService
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class OrderNumberGenerator {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            println("Running Order Number Generator")
            val vertx = Vertx.vertx()
            val confFile = File("conf/ong-conf.json")
            if (!confFile.exists()) {
                throw RuntimeException("Configuration file is missing! (conf/ong-conf.json)")
            }
            var store = ConfigStoreOptions()
                    .setType("file")
                    .setConfig(JsonObject().put("path",confFile.path))
            var retriever = ConfigRetriever.create(vertx, ConfigRetrieverOptions().addStore(store))
            val options = DeploymentOptions()

            retriever.getConfig { ar ->
                if (ar.failed()) {
                    throw RuntimeException("Ooops! Could not find configuration file.")
                } else {
                    val config = ar.result()
                    options.config = config
                    vertx.deployVerticle(ONGService::class.java, options)
                    title()
                }
            }
        }

        fun title() {
            val istream = OrderNumberGenerator::class.java.classLoader.getResourceAsStream("banner.txt")
            BufferedReader(InputStreamReader(istream)).useLines {
                it.forEach { println(it) }
            }
        }

    }

}