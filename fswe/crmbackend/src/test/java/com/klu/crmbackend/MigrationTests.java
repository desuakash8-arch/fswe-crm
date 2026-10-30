package com.klu.crmbackend;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
class MigrationTests {
    @Test void freshSchemaAndRepeatStartup()throws Exception {
        String url="jdbc:h2:mem:migration_fresh;MODE=MySQL;DB_CLOSE_DELAY=-1";
        var flyway=Flyway.configure().dataSource(url,"sa","").load();assertEquals(4,flyway.migrate().migrationsExecuted);assertEquals(0,flyway.migrate().migrationsExecuted);
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement();var r=s.executeQuery("select count(*) from email_jobs")){r.next();assertEquals(0,r.getInt(1));}
    }
    @Test void existingSchemaDataSurvivesMigration()throws Exception {
        String url="jdbc:h2:mem:migration_existing;MODE=MySQL;DB_CLOSE_DELAY=-1";
        var old=Flyway.configure().dataSource(url,"sa","").target("1").load();old.migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()){s.execute("insert into users(name,email,password,role) values('Existing','existing@test.invalid','hash','ADMIN')");}
        assertEquals(3,Flyway.configure().dataSource(url,"sa","").load().migrate().migrationsExecuted);
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement();var r=s.executeQuery("select active,token_version from users where email='existing@test.invalid'")){assertTrue(r.next());assertTrue(r.getBoolean(1));assertEquals(0,r.getInt(2));}
    }
}
