package db.migration;
import org.flywaydb.core.api.migration.*;
import java.sql.*;
public class V2__Crm_workflows extends BaseJavaMigration {
    @Override public Integer getChecksum(){return 1;}
    private void column(Connection c,String table,String column,String type)throws Exception {
        boolean exists=false;
        try(var r=c.getMetaData().getColumns(c.getCatalog(),null,null,null)){while(r.next())if(table.equalsIgnoreCase(r.getString("TABLE_NAME")) && column.equalsIgnoreCase(r.getString("COLUMN_NAME"))){exists=true;break;}}
        if(!exists)try(var s=c.createStatement()){s.execute("ALTER TABLE "+table+" ADD COLUMN "+column+" "+type);}
    }
    @Override public void migrate(Context context)throws Exception {
        var c=context.getConnection();
        column(c,"users","active","BOOLEAN NOT NULL DEFAULT TRUE");column(c,"users","token_version","INT NOT NULL DEFAULT 0");
        column(c,"deals","closed_date","DATE");column(c,"deals","lost_reason","VARCHAR(1000)");
        column(c,"leads","converted_customer_customer_id","BIGINT");column(c,"leads","converted_at","TIMESTAMP");
        column(c,"tasks","interaction_id","BIGINT");column(c,"tasks","customer_customer_id","BIGINT");column(c,"tasks","reminder_key","VARCHAR(255)");
        column(c,"notifications","title","VARCHAR(255)");column(c,"notifications","type","VARCHAR(255)");
        String[] sql={
            "CREATE TABLE audit_events (id BIGINT AUTO_INCREMENT PRIMARY KEY,entity_type VARCHAR(255),entity_id BIGINT,action VARCHAR(255),actor VARCHAR(255),details VARCHAR(2000),created_at TIMESTAMP)",
            "CREATE TABLE email_jobs (id BIGINT AUTO_INCREMENT PRIMARY KEY,recipient VARCHAR(255),subject VARCHAR(255),body LONGTEXT,state VARCHAR(255),attempts INT NOT NULL,deal_id BIGINT,owner_id BIGINT,created_at TIMESTAMP,next_attempt TIMESTAMP,accepted_at TIMESTAMP,last_error VARCHAR(255))",
            "CREATE TABLE password_resets (token_hash VARCHAR(255) PRIMARY KEY,user_id BIGINT,expires_at TIMESTAMP,used BOOLEAN NOT NULL,FOREIGN KEY(user_id) REFERENCES users(user_id))",
            "CREATE UNIQUE INDEX ux_task_interaction ON tasks(interaction_id)",
            "ALTER TABLE tasks ADD CONSTRAINT fk_task_interaction FOREIGN KEY(interaction_id) REFERENCES interactions(interaction_id)",
            "ALTER TABLE tasks ADD CONSTRAINT fk_task_customer FOREIGN KEY(customer_customer_id) REFERENCES customers(customer_id)",
            "ALTER TABLE leads ADD CONSTRAINT fk_lead_conversion FOREIGN KEY(converted_customer_customer_id) REFERENCES customers(customer_id)",
            "CREATE INDEX ix_deal_owner_stage ON deals(assigned_to,stage)",
            "CREATE INDEX ix_deal_closed ON deals(closed_date)",
            "CREATE INDEX ix_task_due_status ON tasks(due_date,status)",
            "CREATE INDEX ix_customer_email ON customers(email)",
            "CREATE INDEX ix_lead_email ON leads(email)",
            "CREATE INDEX ix_email_queue ON email_jobs(state,next_attempt)",
            "CREATE INDEX ix_audit_entity ON audit_events(entity_type,entity_id,created_at)"
        };
        boolean h2=c.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2");
        try(var s=c.createStatement()){for(String statement:sql)s.execute(h2?statement.replace("LONGTEXT","CLOB"):statement);}
    }
}
