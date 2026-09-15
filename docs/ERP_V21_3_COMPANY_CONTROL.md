# GMU EduTrans ERP v21.3 — Company Control Center

Status: additive upgrade. Existing ERP features are preserved and must not be removed.

## Company-wide flow
Owner/Director → Manager → CRM/Sales → Quotation → Payment → Booking → Operations → Crew/Vendor → Trip → Finance → Payroll/Fee → Recruitment/Workforce → Feedback → Repeat Order.

## Locked business guardrails
- Monthly revenue target: Rp50.000.000
- Healthy margin floor: 25%
- Critical margin threshold: 20%
- Pipeline coverage target: 3x monthly revenue target
- Variable incentive cap: 6.25% of cash revenue
- Minimum operating profit after bonus: 10%

## New integrated control layer
- Company Control Center for Owner/Director/Manager/Finance
- Payment Request control for vendor, crew, payroll, commission, bonus, refund, reimbursement, office, tax and other payments
- Compensation lifecycle: ESTIMATED → ACCRUED → ELIGIBLE → PAYABLE → ON_HOLD / PAID / REVERSED
- Recruitment and workforce visibility
- Performance target and snapshot layer
- No-regression CI guard

## Preserved baseline
The following existing capabilities remain mandatory: login/authentication, role privacy, package master, media master, Manager Ops Agent, Trip Folder, document center, trip archive, booking, operation sheet, company operating system, market intelligence, sales target engine, management domains, role playbook, execution control, automation orchestrator, CRM/finance/notification layer, enterprise control tower, business priority, recovery command, company autopilot and target cascade.

## Safety rule
Creating a Payment Request is not the same as transferring money. Sensitive actions such as actual transfer, refund execution, payroll payment, strategic price change, margin below critical threshold, reserve use, permanent hiring/firing, legal and safety-critical decisions stay under human approval.
