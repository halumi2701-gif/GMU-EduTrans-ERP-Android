# One-time refactor runner for ERP v21.1 MainViewModel domain boundaries.
from pathlib import Path
import re

ROOT = Path('app/src/main/java/com/garsyanimultiusaha/gmuedutrans/erp')
MAIN = ROOT / 'MainViewModel.kt'

DOMAIN_METHODS = {
    'SalesActions.kt': [
        'createCustomer', 'createBooking', 'updateBookingStatus',
        'loadCustomerPortalToken', 'clearCustomerPortalToken',
        'startBookingRequestQuotation', 'reviewBookingRequest',
    ],
    'CommerceActions.kt': [
        'openQuotationRequest', 'createQuotationDraft', 'saveQuotationDraft',
        'publishQuotation', 'acceptQuotation', 'rejectQuotation',
        'savePricingPolicy', 'loadQuotationDraftSuggestion',
        'applyQuotationDraftSuggestion', 'savePackageDraft', 'clonePackage',
        'archivePackage', 'applyRecommendedPackagePrice', 'activatePackage',
        'saveCostTemplate', 'setPaymentChannel', 'refreshCommerce',
        'clearQuotationDetail',
    ],
    'FinanceActions.kt': [
        'savePlanningTarget', 'savePlanningBudget', 'deletePlanningBudget',
        'setPlanningPipelineWeight', 'runPlanningScenario',
        'clearPlanningScenario',
    ],
    'OperationsActions.kt': ['insert', 'update', 'approve'],
    'PeopleActions.kt': [
        'createStaff', 'setStaffActive', 'setStaffRole', 'resetStaffPassword',
    ],
}


def find_function_block(src: str, name: str):
    match = re.search(rf'(?m)^    fun {re.escape(name)}\b', src)
    if not match:
        raise SystemExit(f'Function not found: {name}')
    start = match.start()
    brace = src.find('{', match.end())
    if brace < 0:
        raise SystemExit(f'Opening brace not found: {name}')

    depth = 0
    i = brace
    state = 'code'
    while i < len(src):
        c = src[i]
        n = src[i + 1] if i + 1 < len(src) else ''
        tri = src[i:i + 3]
        if state == 'code':
            if tri == '"""':
                state = 'triple'
                i += 3
                continue
            if c == '"':
                state = 'string'
                i += 1
                continue
            if c == "'":
                state = 'char'
                i += 1
                continue
            if c == '/' and n == '/':
                state = 'line'
                i += 2
                continue
            if c == '/' and n == '*':
                state = 'block'
                i += 2
                continue
            if c == '{':
                depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0:
                    end = i + 1
                    while end < len(src) and src[end] in ' \t':
                        end += 1
                    if end < len(src) and src[end] == '\n':
                        end += 1
                    return start, end, src[start:end]
            i += 1
            continue
        if state == 'string':
            if c == '\\':
                i += 2
                continue
            if c == '"':
                state = 'code'
            i += 1
            continue
        if state == 'char':
            if c == '\\':
                i += 2
                continue
            if c == "'":
                state = 'code'
            i += 1
            continue
        if state == 'triple':
            if tri == '"""':
                state = 'code'
                i += 3
                continue
            i += 1
            continue
        if state == 'line':
            if c == '\n':
                state = 'code'
            i += 1
            continue
        if state == 'block':
            if c == '*' and n == '/':
                state = 'code'
                i += 2
                continue
            i += 1
            continue
    raise SystemExit(f'Unclosed function block: {name}')


def to_extension(block: str, name: str) -> str:
    dedented = '\n'.join(
        line[4:] if line.startswith('    ') else line
        for line in block.rstrip().splitlines()
    )
    return re.sub(
        rf'^fun {re.escape(name)}\b',
        f'fun MainViewModel.{name}',
        dedented,
        count=1,
        flags=re.M,
    )


def main():
    for filename in DOMAIN_METHODS:
        if (ROOT / filename).exists():
            raise SystemExit(f'{filename} already exists; refusing duplicate split')

    working = MAIN.read_text()
    extracted = {}
    for filename, names in DOMAIN_METHODS.items():
        blocks = []
        for name in names:
            start, end, block = find_function_block(working, name)
            working = working[:start] + working[end:]
            blocks.append(to_extension(block, name))
        extracted[filename] = blocks

    working = working.replace(
        '    private val api = SupabaseApi()',
        '    internal val api = SupabaseApi()',
        1,
    )
    working = working.replace('        private set', '        internal set')
    working = working.replace(
        '    private fun activeSession(): SessionState?',
        '    internal fun activeSession(): SessionState?',
        1,
    )

    marker = 'class MainViewModel(application: Application) : AndroidViewModel(application) {'
    replacement = marker + '\n    // Session/loading coordinator only; business actions are split by domain extension files.'
    if marker not in working:
        raise SystemExit('MainViewModel class marker missing')
    working = working.replace(marker, replacement, 1)
    MAIN.write_text(working)

    header = (
        'package com.garsyanimultiusaha.gmuedutrans.erp\n\n'
        'import androidx.lifecycle.viewModelScope\n'
        'import kotlinx.coroutines.launch\n\n'
    )
    for filename, blocks in extracted.items():
        (ROOT / filename).write_text(header + '\n\n'.join(blocks) + '\n')

    final_main = MAIN.read_text()
    for names in DOMAIN_METHODS.values():
        for name in names:
            if re.search(rf'(?m)^    fun {re.escape(name)}\b', final_main):
                raise SystemExit(f'{name} still remains in MainViewModel')
    if final_main.count('class MainViewModel') != 1:
        raise SystemExit('MainViewModel class count changed unexpectedly')


if __name__ == '__main__':
    main()
