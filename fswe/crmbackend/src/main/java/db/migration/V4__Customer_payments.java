package db.migration;
import org.flywaydb.core.api.migration.*;
public class V4__Customer_payments extends BaseJavaMigration {
 @Override public Integer getChecksum(){return 1;}
 @Override public void migrate(Context context)throws Exception {
  try(var s=context.getConnection().createStatement()) {
   s.execute("CREATE TABLE payment_invoices (id BIGINT AUTO_INCREMENT PRIMARY KEY, deal_id BIGINT NOT NULL UNIQUE, title VARCHAR(255), customer_name VARCHAR(255), customer_email VARCHAR(255), amount_minor BIGINT NOT NULL, currency VARCHAR(255), status VARCHAR(255), token_hash VARCHAR(64) UNIQUE, link_expires_at TIMESTAMP NULL, receipt VARCHAR(255) UNIQUE, order_id VARCHAR(255) UNIQUE, key_id VARCHAR(255), order_attempted BOOLEAN NOT NULL, payment_id VARCHAR(255) UNIQUE, created_at TIMESTAMP NULL, paid_at TIMESTAMP NULL, FOREIGN KEY(deal_id) REFERENCES deals(deal_id))");
   s.execute("CREATE TABLE payment_attempts (id BIGINT AUTO_INCREMENT PRIMARY KEY, invoice_id BIGINT NOT NULL, payment_id VARCHAR(255) NOT NULL UNIQUE, status VARCHAR(255), method VARCHAR(255), amount_minor BIGINT NOT NULL, refunded_minor BIGINT NOT NULL, updated_at TIMESTAMP NULL, FOREIGN KEY(invoice_id) REFERENCES payment_invoices(id))");
  }
 }
}
