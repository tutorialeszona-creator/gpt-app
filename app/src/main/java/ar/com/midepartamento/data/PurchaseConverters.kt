package ar.com.midepartamento.data

import androidx.room.TypeConverter
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ar.com.midepartamento.domain.PurchaseItem
import org.json.JSONArray
import org.json.JSONObject

class PurchaseConverters {
    @TypeConverter fun encode(items: List<PurchaseItem>): String = JSONArray().apply {
        items.forEach { put(JSONObject().put("name", it.name).put("cents", it.cents ?: JSONObject.NULL)) }
    }.toString()
    @TypeConverter fun decode(json: String): List<PurchaseItem> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            PurchaseItem(item.getString("name"), if (item.isNull("cents")) null else item.getLong("cents"))
        }
    }
}
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN merchant TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE expenses ADD COLUMN items TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE expenses ADD COLUMN receipt TEXT DEFAULT NULL")
    }
}
