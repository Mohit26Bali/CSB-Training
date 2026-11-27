package com.hackathon.enrollment.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.enrollment.dto.CourseRequest;
import com.hackathon.enrollment.dto.CourseResponse;
import com.hackathon.enrollment.dto.CourseStatusCount;
import com.hackathon.enrollment.dto.MessageResponse;
import com.hackathon.enrollment.entity.Course;
import com.hackathon.enrollment.entity.EnrollmentStatus;
import com.hackathon.enrollment.exception.DuplicateCourseException;
import com.hackathon.enrollment.exception.InvalidCapacityException;
import com.hackathon.enrollment.exception.ResourceNotFoundException;
import com.hackathon.enrollment.repo.CourseRepository;
import com.hackathon.enrollment.repo.EnrollmentRepository;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    public CourseService(CourseRepository courseRepository,
                         EnrollmentRepository enrollmentRepository) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> getActiveCourses(String search, String category) {
        return toResponses(filter(courseRepository.findByActiveTrue(), search, category));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<CourseResponse> getAllCourses(String search, String category) {
        return toResponses(filter(courseRepository.findAll(), search, category));
    }

    /** Distinct categories of active courses, alphabetically. */
    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return courseRepository.findByActiveTrue().stream()
                .map(Course::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CourseResponse createCourse(CourseRequest request) {
        String title = request.getTitle().trim();
        if (courseRepository.existsByTitleIgnoreCase(title)) {
            throw new DuplicateCourseException("A course titled '" + title + "' already exists");
        }
        Course course = new Course(title, clean(request.getDescription()),
                request.getCategory().trim(), request.getCapacity());
        course.setImageUrl(clean(request.getImageUrl()));
        if (request.getActive() != null) {
            course.setActive(request.getActive());
        }
        return toResponse(courseRepository.save(course));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CourseResponse updateCourse(Long id, CourseRequest request) {
        // Row lock: an approval cannot slip in between the capacity check and the save.
        Course course = courseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id " + id));

        String title = request.getTitle().trim();
        if (!title.equalsIgnoreCase(course.getTitle())
                && courseRepository.existsByTitleIgnoreCaseAndIdNot(title, id)) {
            throw new DuplicateCourseException("A course titled '" + title + "' already exists");
        }

        long approved = enrollmentRepository.countByCourseAndStatus(course, EnrollmentStatus.APPROVED);
        if (request.getCapacity() < approved) {
            throw new InvalidCapacityException("Capacity cannot be lower than the " + approved
                    + " seat(s) already approved");
        }

        course.setTitle(title);
        course.setDescription(clean(request.getDescription()));
        course.setCategory(request.getCategory().trim());
        course.setCapacity(request.getCapacity());
        course.setImageUrl(clean(request.getImageUrl()));
        if (request.getActive() != null) {
            course.setActive(request.getActive());
        }
        return toResponse(courseRepository.save(course));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public MessageResponse deleteCourse(Long id) {
        Course course = findCourse(id);
        if (enrollmentRepository.existsByCourse(course)) {
            course.setActive(false);
            courseRepository.save(course);
            return new MessageResponse("Course has enrollments, so it was deactivated instead of deleted");
        }
        courseRepository.delete(course);
        return new MessageResponse("Course deleted");
    }

    private Course findCourse(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id " + id));
    }

    /** Case-insensitive match of search against title and description, and of category exactly. */
    private List<Course> filter(List<Course> courses, String search, String category) {
        String term = (search == null) ? "" : search.trim().toLowerCase();
        String cat = (category == null) ? "" : category.trim();
        return courses.stream()
                .filter(c -> term.isEmpty()
                        || c.getTitle().toLowerCase().contains(term)
                        || (c.getDescription() != null && c.getDescription().toLowerCase().contains(term)))
                .filter(c -> cat.isEmpty() || cat.equalsIgnoreCase(c.getCategory()))
                .sorted(java.util.Comparator.comparing(Course::getTitle, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private String clean(String text) {
        return (text == null || text.isBlank()) ? null : text.trim();
    }

    /** Builds responses for many courses using a single grouped count query. */
    private List<CourseResponse> toResponses(List<Course> courses) {
        Map<Long, Map<EnrollmentStatus, Long>> counts = enrollmentRepository.countGroupedByCourseAndStatus()
                .stream()
                .collect(Collectors.groupingBy(CourseStatusCount::courseId,
                        Collectors.toMap(CourseStatusCount::status, CourseStatusCount::total)));
        return courses.stream()
                .map(c -> toResponse(c, counts.getOrDefault(c.getId(), Map.of())))
                .collect(Collectors.toList());
    }

    private CourseResponse toResponse(Course course) {
        Map<EnrollmentStatus, Long> counts = Map.of(
                EnrollmentStatus.APPROVED,
                enrollmentRepository.countByCourseAndStatus(course, EnrollmentStatus.APPROVED),
                EnrollmentStatus.PENDING,
                enrollmentRepository.countByCourseAndStatus(course, EnrollmentStatus.PENDING));
        return toResponse(course, counts);
    }

    private CourseResponse toResponse(Course course, Map<EnrollmentStatus, Long> counts) {
        return new CourseResponse(course.getId(), course.getTitle(), course.getDescription(),
                course.getCategory(), course.getImageUrl(), course.getCapacity(), course.isActive(),
                counts.getOrDefault(EnrollmentStatus.APPROVED, 0L),
                counts.getOrDefault(EnrollmentStatus.PENDING, 0L));
    }
}
