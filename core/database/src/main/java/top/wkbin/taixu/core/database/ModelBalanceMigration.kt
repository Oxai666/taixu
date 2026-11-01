package top.wkbin.taixu.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v86：模型档案新增余额查询的两个自定义字段。
 *
 * - balanceApiPath：余额接口相对路径（拼在 baseUrl 后）；空串表示走内置探测链
 * - balanceResultPath：响应 JSON 里的取值路径；空串同样表示走内置探测
 *
 * 两列都 NOT NULL DEFAULT ''，旧行自动回落「探测模式」，无需回填。
 */
val MIGRATION_85_86 = object : Migration(85, 86) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE harness_models ADD COLUMN balanceApiPath TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE harness_models ADD COLUMN balanceResultPath TEXT NOT NULL DEFAULT ''")
    }
}
