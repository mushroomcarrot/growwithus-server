package community.likelion.growwithusserver.farm.garden;

import community.likelion.growwithusserver.user.data.entity.User;

// 텃밭 도메인과의 접점. 이미 분양된 칸에 담당 관리사를 뒤늦게 배정할 때 그 칸의 텃밭에도 반영한다.
public interface PlotGardenLink {

    // 칸의 텃밭에 담당 관리사가 없으면 채운다. 이미 다른 관리사가 맡고 있으면 PlotAlreadyAssignedException.
    void assignCaretaker(Long plotId, User caretaker);
}
