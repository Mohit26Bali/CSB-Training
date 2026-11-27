package com.cartnest.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.AddressRequest;
import com.cartnest.dto.AddressResponse;
import com.cartnest.entity.Address;
import com.cartnest.exception.BadRequestException;
import com.cartnest.exception.ConflictException;
import com.cartnest.exception.ResourceNotFoundException;
import com.cartnest.repository.AddressRepository;
import com.cartnest.repository.UserRepository;

@Service
@Transactional
public class AddressService {
    static final int MAX_ADDRESSES = 10;

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(AddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long userId) {
        return addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(userId).stream()
                .map(AddressService::toResponse)
                .toList();
    }

    public AddressResponse create(Long userId, AddressRequest request) {
        if (addressRepository.countByUserId(userId) >= MAX_ADDRESSES) {
            throw new ConflictException("You can save at most " + MAX_ADDRESSES + " addresses");
        }
        Address address = new Address(userRepository.getReferenceById(userId));
        apply(address, request);
        boolean first = addressRepository.countByUserId(userId) == 0;
        addressRepository.save(address);
        if (first || Boolean.TRUE.equals(request.makeDefault())) {
            makeDefault(userId, address);
        }
        return toResponse(address);
    }

    public AddressResponse update(Long userId, Long addressId, AddressRequest request) {
        Address address = find(userId, addressId);
        apply(address, request);
        if (Boolean.TRUE.equals(request.makeDefault())) {
            makeDefault(userId, address);
        }
        return toResponse(address);
    }

    public AddressResponse setDefault(Long userId, Long addressId) {
        Address address = find(userId, addressId);
        makeDefault(userId, address);
        return toResponse(address);
    }

    public void delete(Long userId, Long addressId) {
        Address address = find(userId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        addressRepository.delete(address);
        addressRepository.flush();
        if (wasDefault) {
            addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(userId).stream()
                    .findFirst()
                    .ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    private Address find(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address " + addressId + " not found"));
    }

    private void makeDefault(Long userId, Address chosen) {
        addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(userId)
                .forEach(a -> a.setDefaultAddress(a.getId().equals(chosen.getId())));
    }

    private static void apply(Address address, AddressRequest r) {
        String state = IndianStates.canonical(r.state())
                .orElseThrow(() -> new BadRequestException("'" + r.state() + "' is not an Indian state or union territory"));
        address.setFullName(r.fullName().trim());
        address.setPhone(r.phone());
        address.setLine1(r.line1().trim());
        address.setLine2(blankToNull(r.line2()));
        address.setLandmark(blankToNull(r.landmark()));
        address.setCity(r.city().trim());
        address.setState(state);
        address.setPincode(r.pincode());
        address.setType(r.type());
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    static AddressResponse toResponse(Address a) {
        return new AddressResponse(a.getId(), a.getFullName(), a.getPhone(), a.getLine1(), a.getLine2(),
                a.getLandmark(), a.getCity(), a.getState(), a.getPincode(), a.getType(), a.isDefaultAddress());
    }
}
