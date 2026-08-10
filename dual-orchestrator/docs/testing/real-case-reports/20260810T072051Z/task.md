# Captcha Auth App Requirement

## Goal

Create a new Java web application that provides user registration and login by
mobile phone number or email address.

## Technology Baseline

- Java 21
- Spring Boot 3
- Maven
- Thymeleaf server-rendered pages
- PostgreSQL
- Flyway database migrations

## Registration

- A user chooses either a mobile phone number or an email address as the account identifier.
- Registration requires a one-time verification code delivered through the selected channel.
- The user sets a password only after the OTP is successfully verified.
- Mobile numbers and email addresses must be normalized and unique.
- OTPs must expire, be single-use, have bounded verification attempts, and have resend throttling.
- Email delivery must be configurable through SMTP.
- SMS delivery must use a provider interface with configuration isolated from domain logic.
- Automated tests may use deterministic fake delivery adapters, but production pages must never reveal the OTP.

## Login

- A registered user can log in with either the registered mobile number or email address plus password.
- Every login attempt must first pass a server-generated image Captcha.
- Captcha answers must expire, be single-use, and be bound to the current browser session.
- Passwords must be stored with a modern one-way password hash and never logged or persisted as plaintext.
- Successful login establishes an authenticated session; logout invalidates it.
- Invalid Captcha, unknown account, and invalid password must produce safe user-facing errors without exposing account existence unnecessarily.

## User Experience

- Provide polished, responsive registration and login pages.
- Registration supports switching between phone and email without mixing verification state.
- Show validation errors, OTP resend countdown/status, Captcha refresh, loading states, and successful completion.
- Forms must be keyboard accessible and have explicit labels.

## Security And Reliability

- Enable CSRF protection for state-changing form submissions.
- Validate and normalize all server inputs.
- Do not expose credentials, OTP values, password hashes, or provider secrets in responses or logs.
- Apply bounded rate limits to Captcha validation, OTP sending/verification, registration, and login attempts.
- Keep provider credentials in external configuration.

## Required Verification

- Unit tests for normalization, password hashing, Captcha lifecycle, OTP lifecycle, throttling, and authentication decisions.
- Integration tests for registration by email, registration by phone, successful login, invalid/expired Captcha, invalid/expired/reused OTP, duplicate account, invalid password, logout, and protected-page access.
- A reproducible PostgreSQL-backed test or documented local verification path.
- `mvn test` must pass.
- README must document setup, required environment variables, database startup, migrations, and development/test delivery adapters.

## Out Of Scope

- Social login or OAuth providers.
- Password reset and account recovery.
- Purchasing or provisioning a real SMS vendor account.
- Admin consoles, organization management, or multi-factor login after registration.
- Deployment infrastructure beyond local run instructions.

