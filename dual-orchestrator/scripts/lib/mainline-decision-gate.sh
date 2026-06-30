#!/usr/bin/env bash
#
# Mainline Decision Gate parser/validator for mainline stability work.

mainline_decision_gate_field_value() {
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

mainline_decision_gate_required_fields() {
  cat <<'EOF'
Target phase
Current phase status
Upstream promotion evidence
Boundary owner
Source of truth being changed or strengthened
Drift path being removed or centralized
Explicit non-goals
Proof required before claiming completion
Remaining gaps that must stay visible
EOF
}

mainline_decision_gate_missing_fields() {
  local file="$1"
  local missing=()
  local field value
  while IFS= read -r field; do
    [[ -n "$field" ]] || continue
    value="$(mainline_decision_gate_field_value "$file" "$field")"
    if [[ -z "$value" || "$value" == "TODO"* || "$value" == "TBD"* ]]; then
      missing+=("$field")
    fi
  done < <(mainline_decision_gate_required_fields)
  if [[ "${#missing[@]}" -gt 0 ]]; then
    local IFS='|'
    printf '%s' "${missing[*]}"
  fi
}

mainline_decision_gate_tradeoff_flags() {
  local file="$1"
  local flags=()
  local value lower
  while IFS= read -r field; do
    [[ -n "$field" ]] || continue
    value="$(mainline_decision_gate_field_value "$file" "$field")"
    lower="$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')"
    if [[ "$field" == "Target phase" \
        && "$lower" != "p0" \
        && "$lower" != "p0.5" \
        && "$lower" != "p1" \
        && "$lower" != "p2" \
        && "$lower" != "p3" ]]; then
      flags+=("$field")
      continue
    fi
    if [[ "$field" == "Target phase" \
        && "$lower" != "p0" \
        && "$lower" != "p0.5" ]]; then
      flags+=("$field")
      continue
    fi
    if [[ "$field" == "Current phase status" \
        && "$lower" != "implemented" \
        && "$lower" != "verified" \
        && "$lower" != "promoted" ]]; then
      flags+=("$field")
      continue
    fi
    if [[ "$field" == "Upstream promotion evidence" ]]; then
      local phase
      phase="$(printf '%s' "$(mainline_decision_gate_field_value "$file" "Target phase")" | tr '[:upper:]' '[:lower:]')"
      if [[ "$phase" == "p1" || "$phase" == "p2" || "$phase" == "p3" ]]; then
        if [[ "$lower" != *promoted* \
            || "$lower" != *p0* \
            || "$lower" != *p0.5* ]]; then
          flags+=("$field")
          continue
        fi
      elif [[ "$phase" == "p0.5" ]]; then
        if [[ "$lower" != *promoted* || "$lower" != *p0* ]]; then
          flags+=("$field")
          continue
        fi
      fi
    fi
    if [[ "$field" == "Drift path being removed or centralized" \
        && ( "$lower" == "none" \
          || "$lower" == "n/a" \
          || "$lower" == "na" \
          || "$lower" == "no drift" \
          || "$lower" == "not applicable" ) ]]; then
      flags+=("$field")
      continue
    fi
    if [[ "$lower" == *warning* \
        || "$lower" == *prompt* \
        || "$lower" == *fallback* \
        || "$lower" == *measure_only* \
        || "$lower" == *record_only* \
        || "$lower" == *"trace only"* \
        || "$lower" == *"display only"* \
        || "$lower" == *兼容* \
        || "$lower" == *再跑一次* \
        || "$lower" == *看看* \
        || "$lower" == *先观察* \
        || "$lower" == *trade-off* ]]; then
      flags+=("$field")
    fi
  done < <(mainline_decision_gate_required_fields)
  if [[ "${#flags[@]}" -gt 0 ]]; then
    local IFS='|'
    printf '%s' "${flags[*]}"
  fi
}

mainline_decision_gate_is_complete() {
  local file="$1"
  [[ -f "$file" ]] || return 1
  [[ -z "$(mainline_decision_gate_missing_fields "$file")" ]]
  [[ -z "$(mainline_decision_gate_tradeoff_flags "$file")" ]]
}
