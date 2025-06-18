package de.gematik.erp.omemory.converter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class JsonNodeConverter : AttributeConverter<JsonNode, String> {
    private val mapper = jacksonObjectMapper()

    override fun convertToDatabaseColumn(attribute: JsonNode?): String {
        return mapper.writeValueAsString(attribute)
    }

    override fun convertToEntityAttribute(dbData: String?): JsonNode {
        return mapper.readTree(dbData)
    }
}