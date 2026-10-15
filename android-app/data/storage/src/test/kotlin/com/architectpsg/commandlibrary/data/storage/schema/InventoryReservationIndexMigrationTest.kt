package com.architectpsg.commandlibrary.data.storage.schema

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Actual host SQLite validates additive migration and indexed reached-history queries, not a backend. */
class InventoryReservationIndexMigrationTest {
    private fun sql(c:SQLiteConnection,q:String){c.prepare(q).use{it.step()}}
    private fun schema(c:SQLiteConnection,version:Int){
        requireNotNull(javaClass.getResourceAsStream("/inventory-schema$version.sql")).bufferedReader().use{it.readText()}
            .split(";\n").map(String::trim).filter(String::isNotEmpty).forEach{sql(c,it)}
        sql(c,"PRAGMA foreign_keys=ON")
    }
    private fun objects(c:SQLiteConnection):List<List<String?>> = c.prepare("SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name!='room_master_table' ORDER BY type,name").use{s->
        buildList{while(s.step())add((0..3).map{if(s.isNull(it))null else s.getText(it)})}}
    @Test fun actual12To13AddsOnlyCoveringIndexAndIndexesHistoricalIdLookup()=runBlocking{
        BundledSQLiteDriver().open(":memory:").use{c->
            schema(c,12);val before=objects(c)
            sql(c,"INSERT INTO life_profile VALUES('profile',0,'P','p',X'0001FF',7,9)")
            sql(c,"BEGIN IMMEDIATE");Migration12To13.migrate(c);sql(c,"COMMIT")
            assertEquals(before,objects(c).filterNot{it[1]=="index_inventory_change_introduced_id"})
            BundledSQLiteDriver().open(":memory:").use{fresh->schema(fresh,13);assertEquals(objects(fresh),objects(c))}
            c.prepare("EXPLAIN QUERY PLAN SELECT operation_sequence FROM inventory_change WHERE lot_id='reserved' AND before_present=0 AND after_present=1 LIMIT 1").use{s->
                val details=buildList{while(s.step())add(s.getText(3))};assertTrue(details.any{it.contains("COVERING INDEX index_inventory_change_introduced_id")})
                assertFalse(details.any{it.startsWith("SCAN inventory_change")})
            }
            c.prepare("SELECT hex(payload) FROM life_profile").use{assertTrue(it.step());assertEquals("0001FF",it.getText(0))}
        }
    }
    @Test fun indexCreationRollsBackWithoutAlteringExistingSchemaOrData()=runBlocking{
        BundledSQLiteDriver().open(":memory:").use{c->
            schema(c,12);val before=objects(c);sql(c,"BEGIN IMMEDIATE");Migration12To13.migrate(c);sql(c,"ROLLBACK");assertEquals(before,objects(c))
        }
    }
}
