package yokai.domain.source.browse.filter

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addAll
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class Property<T>(
    val get: (T) -> Any?,
    val set: ((T, Any?) -> Unit)? = null,
)

interface Serializer<in T : Filter<out Any?>> {
    fun JsonObjectBuilder.serialize(filter: T) {}
    fun deserialize(json: JsonObject, filter: T) {}

    fun mappings(): List<Pair<String, Property<*>>> = emptyList()

    val serializer: FilterSerializer
    val type: String
    val clazz: Class<out Filter<*>>

    companion object {
        const val TYPE = "_type"
        const val NAME = "name"
        const val STATE = "state"
    }
}

class HeaderSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Header> {
    override val type = "HEADER"
    override val clazz: Class<out Filter<*>> = Filter.Header::class.java

    override fun mappings() = listOf(
        Serializer.NAME to Property<Filter.Header>({ it.name }),
    )
}

class SeparatorSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Separator> {
    override val type = "SEPARATOR"
    override val clazz: Class<out Filter<*>> = Filter.Separator::class.java

    override fun mappings() = listOf(
        Serializer.NAME to Property<Filter.Separator>({ it.name }),
    )
}

class SelectSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Select<Any>> {
    override val type = "SELECT"
    override val clazz: Class<out Filter<*>> = Filter.Select::class.java

    override fun JsonObjectBuilder.serialize(filter: Filter.Select<Any>) {
        putJsonArray("values") {
            addAll(filter.values.map { it.toString() })
        }
    }

    override fun mappings() = listOf(
        Serializer.NAME to Property<Filter.Select<Any>>({ it.name }),
        Serializer.STATE to Property<Filter.Select<Any>>(
            get = { it.state },
            set = { filter, value -> filter.state = value as Int },
        ),
    )
}

class TextSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Text> {
    override val type = "TEXT"
    override val clazz: Class<out Filter<*>> = Filter.Text::class.java

    override fun mappings() = listOf(
        Serializer.NAME to Property<Filter.Text>({ it.name }),
        Serializer.STATE to Property<Filter.Text>(
            get = { it.state },
            set = { filter, value -> filter.state = value as String },
        ),
    )
}

class CheckBoxSerializer(override val serializer: FilterSerializer) : Serializer<Filter.CheckBox> {
    override val type = "CHECKBOX"
    override val clazz: Class<out Filter<*>> = Filter.CheckBox::class.java

    override fun mappings() = listOf(
        Serializer.NAME to Property<Filter.CheckBox>({ it.name }),
        Serializer.STATE to Property<Filter.CheckBox>(
            get = { it.state },
            set = { filter, value -> filter.state = value as Boolean },
        ),
    )
}

class TriStateSerializer(override val serializer: FilterSerializer) : Serializer<Filter.TriState> {
    override val type = "TRI_STATE"
    override val clazz: Class<out Filter<*>> = Filter.TriState::class.java

    override fun mappings() = listOf(
        Serializer.NAME to Property<Filter.TriState>({ it.name }),
        Serializer.STATE to Property<Filter.TriState>(
            get = { it.state },
            set = { filter, value -> filter.state = value as Int },
        ),
    )
}

class GroupSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Group<Any?>> {
    override val type = "GROUP"
    override val clazz: Class<out Filter<*>> = Filter.Group::class.java

    override fun JsonObjectBuilder.serialize(filter: Filter.Group<Any?>) {
        putJsonObject(VALUES) {
            filter.state.forEach { item ->
                @Suppress("UNCHECKED_CAST", "SafeCastWithReturn")
                item as? Filter<Any?> ?: return@forEach
                // Assuming `item.name` is unique, not sure if it is tho...
                put(item.name, serializer.serialize(item))
            }
        }
    }

    override fun deserialize(json: JsonObject, filter: Filter.Group<Any?>) {
        val values = json[VALUES]?.jsonObject
        if (values == null) {
            // TODO: Delete later
            json[Serializer.STATE]?.jsonArray?.forEachIndexed { index, element ->
                if (element == JsonNull) return@forEachIndexed

                @Suppress("UNCHECKED_CAST")
                serializer.deserialize(filter.state[index] as Filter<Any?>, element.jsonObject)
            }
            return
        }

        filter.state.forEach { item ->
            @Suppress("UNCHECKED_CAST", "SafeCastWithReturn")
            item as? Filter<Any?> ?: return@forEach

            val itemJson = values[item.name]?.jsonObject ?: return@forEach
            serializer.deserialize(item, itemJson)
        }
    }

    override fun mappings() = listOf(
        Serializer.NAME to Property<Filter.Group<Any?>>({ it.name }),
    )

    companion object {
        const val VALUES = "values"
    }
}

class SortSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Sort> {
    override val type = "SORT"
    override val clazz: Class<out Filter<*>> = Filter.Sort::class.java

    override fun JsonObjectBuilder.serialize(filter: Filter.Sort) {
        putJsonArray(VALUES) {
            filter.values.forEach { add(it) }
        }

        put(
            Serializer.STATE,
            filter.state?.let { (index, ascending) ->
                buildJsonObject {
                    put(STATE_INDEX, index)
                    put(STATE_ASCENDING, ascending)
                }
            } ?: JsonNull,
        )
    }

    override fun deserialize(json: JsonObject, filter: Filter.Sort) {
        filter.state = (json[Serializer.STATE] as? JsonObject)?.let {
            Filter.Sort.Selection(
                it[STATE_INDEX]!!.jsonPrimitive.int,
                it[STATE_ASCENDING]!!.jsonPrimitive.boolean,
            )
        }
    }

    override fun mappings() = listOf(
        Serializer.NAME to Property<Filter.Sort>({ it.name }),
    )

    companion object {
        const val VALUES = "values"

        const val STATE_INDEX = "index"
        const val STATE_ASCENDING = "ascending"
    }
}
