package ch.simas.monitor.boundry

import ch.simas.monitor.control.MeasurementRepository
import ch.simas.monitor.entity.Measurement
import ch.simas.monitor.xml.Hosts
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.format.DateTimeFormatter
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException
import javax.xml.bind.Unmarshaller
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/check")
open class CheckController(@Value("\${simon.config.hosts}") val config: String, @Autowired val measurementRepository: MeasurementRepository) {

    val latest: Hosts
        @RequestMapping("/latest")
        get() {
            val hosts = loadConfiguration(config)

            hosts.group.forEach { group ->
                group.host.forEach { host ->
                    val measurements = measurementRepository.find(host.url, 1, null, null)
                    if (!measurements.isEmpty()) {
                        val measurement = measurements[0]
                        host.status = measurement.status
                        host.duration = measurement.duration
                        host.timestamp = measurement.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }
                }
            }
            return hosts
        }

    @RequestMapping
    @Scheduled(cron = "*/30 * * * * ?")
    fun check() {
        try {
            val hosts = loadConfiguration(config)

            hosts.group.forEach { group ->
                group.host.forEach { host ->
                    val start = System.currentTimeMillis()
                    val obj = URL(host.url)
                    try {
                        val connection = obj.openConnection() as HttpURLConnection
                        val responseCode = connection.responseCode
                        host.status = "" + responseCode
                        host.duration = System.currentTimeMillis() - start
                    } catch (e: Exception) {
                        host.status = e.message
                    }
                    measurementRepository.createMeasurement(host.name, host.url, host.status, host.duration)
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun loadConfiguration(configFile: String): Hosts {
        try {
            val file = File(configFile)
            val jaxbContext = JAXBContext.newInstance(Hosts::class.java)

            val unmarshaller = jaxbContext.createUnmarshaller()
            val hosts = unmarshaller.unmarshal(file) as Hosts
            return hosts
        } catch (e: JAXBException) {
            throw RuntimeException(e)
        }

    }

}
