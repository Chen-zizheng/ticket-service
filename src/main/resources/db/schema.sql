DROP TABLE IF EXISTS assignment;
DROP TABLE IF EXISTS ticket;
DROP TABLE IF EXISTS assignee;

CREATE TABLE assignee (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  name        VARCHAR(32)  NOT NULL,
  team        VARCHAR(32)  NOT NULL,
  active      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE ticket (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  title        VARCHAR(120) NOT NULL,
  description  VARCHAR(2000),
  priority     VARCHAR(8)   NOT NULL,
  status       VARCHAR(12)  NOT NULL,
  assignee_id  BIGINT,
  version      INT          NOT NULL DEFAULT 0,
  created_at   TIMESTAMP    NOT NULL,
  updated_at   TIMESTAMP    NOT NULL,
  resolved_at  TIMESTAMP,
  CONSTRAINT fk_ticket_assignee FOREIGN KEY (assignee_id) REFERENCES assignee (id)
);

CREATE INDEX idx_ticket_status_created ON ticket (status, created_at DESC);
CREATE INDEX idx_ticket_assignee       ON ticket (assignee_id);
CREATE INDEX idx_ticket_priority       ON ticket (priority);

CREATE TABLE assignment (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_id   BIGINT      NOT NULL,
  assignee_id BIGINT      NOT NULL,
  note        VARCHAR(200),
  created_at  TIMESTAMP   NOT NULL,
  CONSTRAINT fk_assignment_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id)
);

CREATE INDEX idx_assignment_ticket ON assignment (ticket_id);
