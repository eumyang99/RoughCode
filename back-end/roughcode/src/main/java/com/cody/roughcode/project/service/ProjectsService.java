package com.cody.roughcode.project.service;

import com.cody.roughcode.project.dto.req.FeedbackReq;
import com.cody.roughcode.project.dto.req.FeedbackUpdateReq;
import com.cody.roughcode.project.dto.res.FeedbackInfoRes;
import com.cody.roughcode.project.dto.res.ProjectInfoRes;
import com.cody.roughcode.project.dto.req.ProjectReq;
import com.cody.roughcode.project.dto.res.ProjectDetailRes;
import com.cody.roughcode.project.dto.res.ProjectTagsRes;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ProjectsService {
    Long insertProject(ProjectReq req, Long usersId);
    int updateProjectThumbnail(MultipartFile thumbnail, Long projectsId, Long usersId);
    int updateProject(ProjectReq req, Long usersId);
    int connect(Long projectsId, Long usersId, List<Long> codesIdList);
    int deleteProject(Long projectsId, Long usersId);
    List<ProjectInfoRes> getProjectList(String sort, PageRequest pageRequest, String keyword, String tagIds, int closed);
    ProjectDetailRes getProject(Long projectId, Long usersId);
    int likeProject(Long projectsId, Long usersId);

    int insertFeedback(FeedbackReq req, Long usersId);
    Boolean updateFeedback(FeedbackUpdateReq req, Long userId);
    List<FeedbackInfoRes> getFeedbackList(Long projectId, Long usersId);
    int deleteFeedback(Long feedbackId, Long usersId);
    int feedbackComplain(Long feedbackId, Long usersId);

    int isProjectOpen(Long projectId);
    Boolean checkProject(String url, Long usersId) throws IOException;

    List<ProjectTagsRes> searchTags(String s);
}
