package com.fouad.cuttingnotebook.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun orderStatusToString(value: OrderStatus): String = value.name

    @TypeConverter
    fun stringToOrderStatus(value: String): OrderStatus = OrderStatus.valueOf(value)

    @TypeConverter
    fun entryMethodToString(value: EntryMethod): String = value.name

    @TypeConverter
    fun stringToEntryMethod(value: String): EntryMethod = EntryMethod.valueOf(value)
}

@Database(
    entities = [
        ModelEntity::class,
        CutOrderEntity::class,
        OrderItemEntity::class,
        CutOperationEntity::class,
    ],
    version = CuttingDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CuttingDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun orderDao(): OrderDao
    abstract fun orderItemDao(): OrderItemDao
    abstract fun operationDao(): OperationDao

    companion object {
        /**
         * Bump alongside a migration, never with destructive fallback: the notebook is the
         * only copy of this production record (spec 19.9).
         */
        const val VERSION = 1
        const val NAME = "cutting-notebook.db"

        fun build(context: Context): CuttingDatabase =
            Room.databaseBuilder(context.applicationContext, CuttingDatabase::class.java, NAME)
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
    }
}
