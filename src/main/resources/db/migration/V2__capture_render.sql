-- PCL->PDF rendering artifacts (docs/decisions.md D-018, supersedes D-003).
-- storage_key keeps pointing at the primary/original artifact (.pcl for PCL);
-- rendered_storage_key points at the rendered PDF sibling. Dedup is unchanged —
-- it is computed on the original bytes only.

ALTER TABLE capture
    ADD COLUMN rendered_storage_key text,
    ADD COLUMN render_status text NOT NULL DEFAULT 'SKIPPED'
        CHECK (render_status IN ('SKIPPED','SUCCESS','FAILED')),
    ADD COLUMN render_error text;
