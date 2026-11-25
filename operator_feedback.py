#!/usr/bin/env python3
"""Record/export human review labels; does not retrain or update models automatically."""
import argparse
import csv
import sqlite3

VALID = {"confirmed_defect", "false_alarm", "missed_defect", "confirmed_good", "uncertain"}

def main():
    parser = argparse.ArgumentParser(description="Record or export human QC feedback")
    parser.add_argument("--db", default="inspections.sqlite3")
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--event-id")
    parser.add_argument("--verdict", choices=sorted(VALID))
    parser.add_argument("--export")
    args = parser.parse_args()
    db = sqlite3.connect(args.db)
    columns = {row[1] for row in db.execute("PRAGMA table_info(inspections)")}
    if columns and "operator_feedback" not in columns:
        db.execute("ALTER TABLE inspections ADD COLUMN operator_feedback TEXT")
        db.commit()
    if args.list:
        for row in db.execute("SELECT event_id,timestamp_utc,product_id,decision,defects_json FROM inspections ORDER BY timestamp_utc DESC LIMIT 20"):
            print(row)
    if args.event_id or args.verdict:
        if not (args.event_id and args.verdict):
            parser.error("Provide --event-id and --verdict together")
        if db.execute("SELECT 1 FROM inspections WHERE event_id=?", (args.event_id,)).fetchone() is None:
            parser.error("Unknown event ID")
        db.execute("UPDATE inspections SET operator_feedback=? WHERE event_id=?", (args.verdict, args.event_id))
        db.commit()
        print("Feedback recorded; model is unchanged.")
    if args.export:
        rows = list(db.execute("SELECT event_id,timestamp_utc,product_id,decision,defects_json,operator_feedback FROM inspections WHERE operator_feedback IS NOT NULL"))
        with open(args.export, "w", newline="", encoding="utf-8") as handle:
            writer = csv.writer(handle)
            writer.writerow(["event_id","timestamp_utc","product_id","decision","defects_json","operator_feedback"])
            writer.writerows(rows)
        print(f"Exported {len(rows)} feedback rows to {args.export}")
    db.close()

if __name__ == "__main__":
    main()
