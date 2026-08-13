package com.eventbooking.integration;

import com.eventbooking.dto.auth.RegisterRequest;
import com.eventbooking.entity.*;
import com.eventbooking.repository.*;
import com.eventbooking.service.RazorpayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Exercises the actual REST API end-to-end - register, log in, lock a seat,
 * create a booking, verify payment, confirm it's actually booked - against
 * real MySQL and Redis. The only thing mocked is RazorpayService, since its
 * createOrder() method makes a genuine outbound call to Razorpay's API that
 * this test environment has no credentials or network access for.
 * verifySignature's actual HMAC logic is exhaustively covered separately in
 * RazorpayServiceTest - here we only need the *flow* to behave correctly
 * when verification succeeds, which is why it's stubbed to return true
 * rather than re-deriving a real signature.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FullBookingFlowIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private VenueRepository venueRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventSeatRepository eventSeatRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockBean
    private RazorpayService razorpayService;

    private Long eventId;
    private Long eventSeatId;

    @BeforeEach
    void setUp() {
        User admin = userRepository.save(User.builder()
                .name("Flow Test Admin").email("flow-test-admin@example.com")
                .passwordHash(passwordEncoder.encode("password"))
                .role(Role.ADMIN)
                .build());

        Venue venue = venueRepository.save(Venue.builder()
                .name("Flow Test Venue").address("1 Test Ave").city("Delhi")
                .totalCapacity(1)
                .build());

        Seat seat = seatRepository.save(Seat.builder()
                .venue(venue).rowLabel("A").seatNumber(1).tier(SeatTier.GOLD)
                .build());

        Event event = eventRepository.save(Event.builder()
                .venue(venue).title("Flow Test Concert").category("Concert")
                .eventDate(LocalDateTime.now().plusDays(10))
                .status(EventStatus.UPCOMING)
                .createdBy(admin)
                .build());

        EventSeat eventSeat = eventSeatRepository.save(EventSeat.builder()
                .event(event).seat(seat)
                .price(new BigDecimal("1500"))
                .status(SeatStatus.AVAILABLE)
                .build());

        eventId = event.getId();
        eventSeatId = eventSeat.getId();

        when(razorpayService.createOrder(any(BigDecimal.class), anyString())).thenReturn("order_test_123");
        when(razorpayService.verifySignature(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void registerLockBookAndVerifyPayment_confirmsTheBookingAndMarksTheSeatBooked() {
        String email = "flow-test-user@example.com";
        registerUser(email, "password123");
        String token = login(email, "password123");
        HttpHeaders authHeaders = bearerHeaders(token);

        // 1. Lock the seat
        var lockResponse = restTemplate.exchange(
                url("/api/events/" + eventId + "/seats/" + eventSeatId + "/lock"),
                HttpMethod.POST, new HttpEntity<>(authHeaders), Map.class);
        assertThat(lockResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat((Boolean) lockResponse.getBody().get("locked")).isTrue();

        // 2. Create a PENDING booking + (mocked) Razorpay order
        Map<String, Object> bookingRequest = Map.of("eventId", eventId, "eventSeatIds", java.util.List.of(eventSeatId));
        var createResponse = restTemplate.exchange(
                url("/api/bookings"), HttpMethod.POST,
                new HttpEntity<>(bookingRequest, authHeaders), Map.class);
        assertThat(createResponse.getStatusCode().is2xxSuccessful()).isTrue();
        Number bookingId = (Number) createResponse.getBody().get("bookingId");
        assertThat(createResponse.getBody().get("razorpayOrderId")).isEqualTo("order_test_123");

        // Seat must NOT be booked yet - only after payment verification
        EventSeat stillAvailable = eventSeatRepository.findById(eventSeatId).orElseThrow();
        assertThat(stillAvailable.getStatus()).isEqualTo(SeatStatus.AVAILABLE);

        // 3. Verify payment (signature check mocked to succeed)
        Map<String, Object> verifyRequest = Map.of(
                "bookingId", bookingId,
                "razorpayOrderId", "order_test_123",
                "razorpayPaymentId", "pay_test_456",
                "razorpaySignature", "any_signature_since_verification_is_mocked");
        var verifyResponse = restTemplate.exchange(
                url("/api/bookings/verify-payment"), HttpMethod.POST,
                new HttpEntity<>(verifyRequest, authHeaders), Map.class);

        assertThat(verifyResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(verifyResponse.getBody().get("status")).isEqualTo("CONFIRMED");

        // 4. Seat should now genuinely be BOOKED in the database
        EventSeat finalSeat = eventSeatRepository.findById(eventSeatId).orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.BOOKED);
    }

    @Test
    void secondUser_cannotLockASeatAlreadyHeldByTheFirst() {
        registerUser("flow-user-a@example.com", "password123");
        registerUser("flow-user-b@example.com", "password123");
        String tokenA = login("flow-user-a@example.com", "password123");
        String tokenB = login("flow-user-b@example.com", "password123");

        var firstLock = restTemplate.exchange(
                url("/api/events/" + eventId + "/seats/" + eventSeatId + "/lock"),
                HttpMethod.POST, new HttpEntity<>(bearerHeaders(tokenA)), Map.class);
        assertThat((Boolean) firstLock.getBody().get("locked")).isTrue();

        var secondLock = restTemplate.exchange(
                url("/api/events/" + eventId + "/seats/" + eventSeatId + "/lock"),
                HttpMethod.POST, new HttpEntity<>(bearerHeaders(tokenB)), Map.class);
        assertThat((Boolean) secondLock.getBody().get("locked")).isFalse();
    }

    private void registerUser(String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setName("Flow Test User");
        request.setEmail(email);
        request.setPassword(password);
        restTemplate.postForEntity(url("/api/auth/register"), request, Map.class);

        // Registration no longer auto-verifies (see AuthService) - OTP delivery
        // is logged, not returned via any API, so it can't be captured here.
        // These tests care about the booking flow, not the OTP flow itself
        // (that's AuthServiceTest's job), so we mark the account verified
        // directly rather than trying to intercept a logged code.
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setEmailVerified(true);
            userRepository.save(user);
        });
    }

    private String login(String email, String password) {
        Map<String, String> loginRequest = Map.of("email", email, "password", password);
        var response = restTemplate.postForEntity(url("/api/auth/login"), loginRequest, Map.class);
        return (String) response.getBody().get("token");
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
