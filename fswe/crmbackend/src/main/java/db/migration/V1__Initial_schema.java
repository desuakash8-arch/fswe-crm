package db.migration;
import org.flywaydb.core.api.migration.*;
public class V1__Initial_schema extends BaseJavaMigration {
    @Override public Integer getChecksum(){return 1;}
    @Override public void migrate(Context context)throws Exception {
        String[] statements={
            "CREATE TABLE IF NOT EXISTS companies (company_id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(255) NOT NULL,email VARCHAR(255),phone VARCHAR(255),address VARCHAR(255),status VARCHAR(255),created_at TIMESTAMP)",
            "CREATE TABLE IF NOT EXISTS users (user_id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(255) NOT NULL,email VARCHAR(255) NOT NULL UNIQUE,password VARCHAR(255) NOT NULL,role VARCHAR(255) NOT NULL,company_id BIGINT,manager_id BIGINT,created_at TIMESTAMP,FOREIGN KEY(company_id) REFERENCES companies(company_id),FOREIGN KEY(manager_id) REFERENCES users(user_id))",
            "CREATE TABLE IF NOT EXISTS customers (customer_id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(255) NOT NULL,email VARCHAR(255),phone VARCHAR(255),company VARCHAR(255),address VARCHAR(255),status VARCHAR(255),assigned_to BIGINT,created_at TIMESTAMP,FOREIGN KEY(assigned_to) REFERENCES users(user_id))",
            "CREATE TABLE IF NOT EXISTS leads (lead_id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(255) NOT NULL,email VARCHAR(255),phone VARCHAR(255),company VARCHAR(255),source VARCHAR(255),status VARCHAR(255),assigned_to BIGINT,created_at TIMESTAMP,FOREIGN KEY(assigned_to) REFERENCES users(user_id))",
            "CREATE TABLE IF NOT EXISTS deals (deal_id BIGINT AUTO_INCREMENT PRIMARY KEY,title VARCHAR(255),amount DECIMAL(38,2),stage VARCHAR(255),customer_id BIGINT,assigned_to BIGINT,expected_close_date DATE,created_at TIMESTAMP,FOREIGN KEY(customer_id) REFERENCES customers(customer_id),FOREIGN KEY(assigned_to) REFERENCES users(user_id))",
            "CREATE TABLE IF NOT EXISTS interactions (interaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,customer_id BIGINT,user_id BIGINT,type VARCHAR(255),notes VARCHAR(255),interaction_date TIMESTAMP,next_followup TIMESTAMP,FOREIGN KEY(customer_id) REFERENCES customers(customer_id),FOREIGN KEY(user_id) REFERENCES users(user_id))",
            "CREATE TABLE IF NOT EXISTS tasks (task_id BIGINT AUTO_INCREMENT PRIMARY KEY,title VARCHAR(255),description VARCHAR(255),assigned_to BIGINT,due_date DATE,status VARCHAR(255),priority VARCHAR(255),FOREIGN KEY(assigned_to) REFERENCES users(user_id))",
            "CREATE TABLE IF NOT EXISTS notifications (notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,title VARCHAR(255),type VARCHAR(255),message VARCHAR(255),is_read BOOLEAN,created_at TIMESTAMP,FOREIGN KEY(user_id) REFERENCES users(user_id))"
        };
        try(var s=context.getConnection().createStatement()){for(String sql:statements)s.execute(sql);}
    }
}
