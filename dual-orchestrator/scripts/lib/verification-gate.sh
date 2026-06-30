#!/usr/bin/env bash
#
# Verification Gate parser/validator for real provider runs.

verification_gate_field_value() {
  local file="$1"
  local field="$2"
  awk -v field="$field" '
    $0 ~ "^[[:space:]]*-[[:space:]]*" field ":[[:space:]]*" {
      sub("^[[:space:]]*-[[:space:]]*" field ":[[:space:]]*", "")
      print
      exit
    }
  ' "$file"
}

verification_gate_required_fields() {
  cat <<'EOF'
Target
Source of truth
Trigger precondition
How precondition is checked before run
PASS
FAIL
INVALID
Stop-loss
Run artifact freshness
EOF
}

verification_gate_missing_fields() {
  local file="$1"
  local missing=()
  local field value
  while IFS= read -r field; do
    [[ -n "$field" ]] || continue
    value="$(verification_gate_field_value "$file" "$field")"
    if [[ -z "$value" || "$value" == "TODO"* || "$value" == "TBD"* ]]; then
      missing+=("$field")
    fi
  done < <(verification_gate_required_fields)
  if [[ "${#missing[@]}" -gt 0 ]]; then
    local IFS='|'
    printf '%s' "${missing[*]}"
  fi
}

verification_gate_is_complete() {
  local file="$1"
  [[ -f "$file" ]] || return 1
  [[ -z "$(verification_gate_missing_fields "$file")" ]]
}
