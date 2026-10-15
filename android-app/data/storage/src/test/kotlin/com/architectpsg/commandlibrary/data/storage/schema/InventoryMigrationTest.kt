package com.architectpsg.commandlibrary.data.storage.schema

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Actual bundled host SQLite, actual KSP11/12 DDL; opaque fixtures do not prove backend semantics. */
class InventoryMigrationTest {
    @get:Rule val folder=TemporaryFolder()
    private fun sql(c:SQLiteConnection,q:String){c.prepare(q).use{it.step()}}
    private fun number(c:SQLiteConnection,q:String):Long=c.prepare(q).use{assertTrue(it.step());it.getLong(0)}
    private fun schema(c:SQLiteConnection,version:Int){
        val name=if(version==11)"deck-edits-schema11.sql" else "inventory-schema12.sql"
        requireNotNull(javaClass.getResourceAsStream("/$name")).bufferedReader().use{it.readText()}.split(";\n")
            .map(String::trim).filter(String::isNotEmpty).forEach{sql(c,it)}
        sql(c,"PRAGMA foreign_keys=ON");sql(c,"PRAGMA user_version=$version")
    }
    private fun objects(c:SQLiteConnection,oldOnly:Boolean=false):List<List<String?>> = c.prepare(
        "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name!='room_master_table'"+
        (if(oldOnly)" AND tbl_name NOT LIKE 'inventory_%'" else "")+" ORDER BY type,name").use{s->
        buildList{while(s.step())add((0..3).map{if(s.isNull(it))null else s.getText(it)})}}
    private fun operation(c:SQLiteConnection,id:String="op",base:String="NULL",target:String="NULL",kind:String="APPLY"){
        sql(c,"""INSERT INTO inventory_operation(operation_id,kind,request_version,request_sha256,expected_revision,result_revision,
            modified_seconds,modified_nanos,content_sha256,change_count,merge_count,reservation_count,changes_sha256,
            descriptor_length,descriptor_version,descriptor_sha256,descriptor,base_group_sequence,undo_target_sequence)
            VALUES('$id','$kind',1,'opaque',0,1,1,0,'opaque',1,0,0,'opaque',1,1,'opaque',X'01',$base,$target)""")
    }
    private fun group(c:SQLiteConnection){sql(c,"INSERT INTO inventory_group VALUES(1,NULL,'empty','opaque')")}
    private fun lot(c:SQLiteConnection){sql(c,"INSERT INTO inventory_lot VALUES('reserved',0,1,0,3,1,'opaque',X'0001FF',1)")}
    private fun change(c:SQLiteConnection){sql(c,"""INSERT INTO inventory_change(operation_sequence,lot_id,before_present,after_present,
        after_active,after_revision,after_quantity,after_payload_length,after_payload_version,after_payload_sha256,after_payload)
        VALUES(1,'reserved',0,1,0,1,0,3,1,'opaque',X'0001FF')""")}
    private fun state(c:SQLiteConnection){sql(c,"INSERT INTO inventory_state VALUES(1,1,1,0,1,1,'opaque')")}
    @Test fun actualExportMigrationPreservesAllFortyOldTablesAndBytesThenReopens()=runBlocking{
        val file=folder.newFile()
        BundledSQLiteDriver().open(file.path).use{c->
            schema(c,11);sql(c,"INSERT INTO life_profile VALUES('profile',0,'É','é',X'0001FF',7,9)");val old=objects(c)
            sql(c,"BEGIN IMMEDIATE");Migration11To12.migrate(c);sql(c,"PRAGMA user_version=12");sql(c,"COMMIT")
            assertEquals(old,objects(c,true));assertEquals(5L,number(c,"SELECT count(*) FROM sqlite_master WHERE type='table' AND name LIKE 'inventory_%'"))
            BundledSQLiteDriver().open(":memory:").use{fresh->schema(fresh,12);assertEquals(objects(fresh),objects(c))}
            for(table in listOf("inventory_state","inventory_operation","inventory_lot","inventory_group","inventory_change"))assertEquals(0L,number(c,"SELECT count(*) FROM $table"))
        }
        BundledSQLiteDriver().open(file.path).use{c->
            sql(c,"PRAGMA foreign_keys=ON");assertEquals(12L,number(c,"PRAGMA user_version"))
            c.prepare("SELECT hex(payload) FROM life_profile").use{assertTrue(it.step());assertEquals("0001FF",it.getText(0))}
            assertEquals(0L,number(c,"SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }
    @Test fun immediateForeignKeysAllowOrderedJournalAndRetainedReservationButRejectMissingReferences()=runBlocking{
        BundledSQLiteDriver().open(":memory:").use{c->
            schema(c,12)
            assertThrows(SQLiteException::class.java){group(c)}
            assertThrows(SQLiteException::class.java){operation(c,"bad","99")}
            assertThrows(SQLiteException::class.java){lot(c)}
            operation(c);change(c);group(c);lot(c);state(c)
            operation(c,"undo","1","1","UNDO")
            assertThrows(SQLiteException::class.java){operation(c)}
            assertThrows(SQLiteException::class.java){sql(c,"DELETE FROM inventory_group")}
            assertThrows(SQLiteException::class.java){sql(c,"DELETE FROM inventory_operation WHERE sequence=1")}
            assertEquals(0L,number(c,"SELECT active FROM inventory_lot WHERE lot_id='reserved'"))
            assertEquals(2L,number(c,"SELECT max(sequence) FROM inventory_operation"))
            assertEquals(0L,number(c,"SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }
    @Test fun migrationAndEveryPartialJournalPrefixRollBackWithoutSyntheticState()=runBlocking{
        BundledSQLiteDriver().open(":memory:").use{c->
            schema(c,11);val old=objects(c)
            sql(c,"BEGIN IMMEDIATE");Migration11To12.migrate(c);sql(c,"PRAGMA user_version=12")
            assertThrows(SQLiteException::class.java){state(c)}
            sql(c,"ROLLBACK");assertEquals(old,objects(c));assertEquals(11L,number(c,"PRAGMA user_version"))
            Migration11To12.migrate(c)
            for(prefix in 1..5){
                sql(c,"BEGIN IMMEDIATE");operation(c)
                if(prefix>=2)change(c);if(prefix>=3)group(c);if(prefix>=4)lot(c);if(prefix>=5)state(c)
                sql(c,"ROLLBACK")
                for(table in listOf("inventory_operation","inventory_change","inventory_group","inventory_lot","inventory_state"))assertEquals(0L,number(c,"SELECT count(*) FROM $table"))
            }
        }
    }
}
